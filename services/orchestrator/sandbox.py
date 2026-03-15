"""Code execution sandbox via Podman container API."""

import asyncio
import json
import logging
import tempfile
from pathlib import Path

import httpx

logger = logging.getLogger(__name__)

PODMAN_SOCKET = "/run/podman/podman.sock"
SANDBOX_IMAGE = "gizmo-sandbox:latest"
API_BASE = "http://d/v5.0.0"
DEFAULT_TIMEOUT = 10
MAX_TIMEOUT = 30
MAX_OUTPUT = 8000


async def _api(method: str, path: str, **kwargs) -> httpx.Response:
    """Make a request to the Podman API over Unix socket."""
    transport = httpx.AsyncHTTPTransport(uds=PODMAN_SOCKET)
    async with httpx.AsyncClient(transport=transport, timeout=30.0) as client:
        return await client.request(method, f"{API_BASE}{path}", **kwargs)


async def run_code(code: str, timeout: int = DEFAULT_TIMEOUT) -> dict:
    """Execute Python code in an isolated container.

    Returns dict with stdout, stderr, exit_code, and timed_out.
    """
    timeout = max(1, min(timeout, MAX_TIMEOUT))

    # Create container with strict resource limits
    create_body = {
        "Image": SANDBOX_IMAGE,
        "Cmd": ["python3", "-c", code],
        "NetworkDisabled": True,
        "HostConfig": {
            "Memory": 256 * 1024 * 1024,  # 256MB
            "NanoCpus": 1_000_000_000,  # 1.0 CPU
            "PidsLimit": 100,
            "ReadonlyRootfs": True,
            "Tmpfs": {"/tmp": "size=50m"},
            "SecurityOpt": ["no-new-privileges"],
        },
    }

    container_id = None
    try:
        # Create
        resp = await _api("POST", "/containers/create", json=create_body)
        if resp.status_code != 201:
            return {"stdout": "", "stderr": f"Container create failed: {resp.text}", "exit_code": -1, "timed_out": False}
        container_id = resp.json()["Id"]

        # Start
        resp = await _api("POST", f"/containers/{container_id}/start")
        if resp.status_code not in (200, 204):
            return {"stdout": "", "stderr": f"Container start failed: {resp.text}", "exit_code": -1, "timed_out": False}

        # Wait with timeout
        timed_out = False
        try:
            resp = await _api("POST", f"/containers/{container_id}/wait", timeout=timeout + 5)
            exit_code = resp.json().get("StatusCode", -1)
        except (httpx.ReadTimeout, asyncio.TimeoutError):
            timed_out = True
            exit_code = -1
            # Kill the container
            try:
                await _api("POST", f"/containers/{container_id}/kill")
            except Exception:
                pass

        # Read logs
        stdout = ""
        stderr = ""
        try:
            resp = await _api("GET", f"/containers/{container_id}/logs", params={"stdout": "true", "stderr": "true"})
            # Podman multiplexed log stream: 8-byte header per frame
            raw = resp.content
            pos = 0
            while pos + 8 <= len(raw):
                stream_type = raw[pos]  # 1=stdout, 2=stderr
                size = int.from_bytes(raw[pos + 4:pos + 8], "big")
                pos += 8
                chunk = raw[pos:pos + size].decode("utf-8", errors="replace")
                pos += size
                if stream_type == 1:
                    stdout += chunk
                elif stream_type == 2:
                    stderr += chunk
        except Exception as e:
            logger.warning("Failed to read container logs: %s", e)

        return {
            "stdout": stdout[:MAX_OUTPUT],
            "stderr": stderr[:MAX_OUTPUT],
            "exit_code": exit_code,
            "timed_out": timed_out,
        }
    except Exception as e:
        logger.error("Sandbox error: %s", e)
        return {"stdout": "", "stderr": f"Sandbox error: {str(e)}", "exit_code": -1, "timed_out": False}
    finally:
        # Cleanup container
        if container_id:
            try:
                await _api("DELETE", f"/containers/{container_id}", params={"force": "true"})
            except Exception:
                pass
