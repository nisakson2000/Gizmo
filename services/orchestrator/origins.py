import os
from starlette.websockets import WebSocket

ALLOWED_ORIGINS = [
    o.strip()
    for o in os.environ.get(
        "ALLOWED_ORIGINS",
        "http://localhost:3100,https://bazzite.tail163501.ts.net",
    ).split(",")
    if o.strip()
]


def check_ws_origin(ws: WebSocket) -> bool:
    origin = (ws.headers.get("origin") or "").rstrip("/")
    return any(origin == allowed.rstrip("/") for allowed in ALLOWED_ORIGINS)
