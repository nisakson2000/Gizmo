# Gizmo-AI V1 — Audit Report
**Date:** 2026-03-13
**Auditor:** Claude Code
**Build:** Gizmo-AI v1, Huihui-Qwen3.5-27B-abliterated.i1-Q5_K_M.gguf

## Summary

| Category | Status | Notes |
|----------|--------|-------|
| 1. Infrastructure Health | WARN | All 6 services healthy. VRAM at 22120 MiB (over 22GB threshold). Whisper reports unhealthy in podman but responds OK. |
| 2. Model Response | PASS | Non-streaming and streaming both work. Model auto-thinks so needs adequate max_tokens. |
| 3. Thinking Mode | FAIL | Thinking ON works. Thinking OFF (`enable_thinking: false`) does NOT suppress thinking — model thinks regardless. |
| 4. WebSocket Streaming | PASS | All 5 protocol checks pass. Event order correct. Orchestrator restart recovery <5s. |
| 5. Tool Calling | PASS | web_search, write_memory, read_memory, list_memories all functional. SearXNG returns results. |
| 6. Whisper Transcription | PASS | Direct endpoint and orchestrator proxy both return valid JSON. |
| 7. Kokoro TTS | PASS | Direct: 16,941 byte audio. Orchestrator proxy: 16,869 byte MPEG audio. Endpoint uses Form data, not JSON. |
| 8. Vision/Image Upload | FAIL | mmproj GGUF exists on disk but llama.cpp not started with `--mmproj` flag. Upload endpoint works, but model cannot process images. |
| 9. Conversation Persistence | PASS | SQLite survives restart. Context recalled correctly across restart boundary. |
| 10. UI Functionality | PASS* | Page loads, API/WS proxy works. *Manual browser testing required for interactive elements. Known nginx DNS cache issue on container restart. |
| 11. GitHub Presence | PASS* | Repo public, all files present, profile README updated. *No repository topics set. |
| 12. Tailscale Remote Access | PASS | UI and orchestrator reachable at http://100.69.89.10:3100. Firewall zone: trusted. |

**Overall: 9/12 categories passing (1 WARN, 2 FAIL)**

## VRAM Usage
- Model loaded: 22120 MiB used / 24079 MiB total
- Headroom: 1961 MiB free (~8% of total)
- **WARNING:** Exceeds 22GB threshold. Parallel requests may cause OOM. Consider reducing `--parallel` from 4 to 2, or using a smaller quant (Q4_K_M).

## Discrepancies

1. **Thinking mode cannot be disabled.** The constitution and wiki claim thinking mode is toggleable via `enable_thinking` parameter. In practice, the Qwen3.5-27B model thinks regardless of this parameter. The `reasoning_content` field is always populated. The UI toggle changes the parameter sent to llama.cpp, but the model ignores it. This is a model-level behavior, not a code bug.

2. **Vision is not functional.** The mmproj GGUF file (629MB, Q8_0) was downloaded and exists at `~/gizmo-ai/models/mmproj/Huihui-Qwen3.5-27B-abliterated.mmproj-Q8_0.gguf`, but the llama.cpp container command does not include `--mmproj`. The model server starts without vision capabilities. The upload endpoint works (returns base64 data URL), but the model responds with "I don't see an image."

3. **No GitHub topics.** The `repositoryTopics` field is null. Expected 5+ topics (e.g., ai, llm, local-ai, llama-cpp, self-hosted).

4. **Orchestrator health endpoint lacks model name.** Returns `{"status":"ok","service":"gizmo-orchestrator"}` — no model name or version info.

5. **TTS orchestrator endpoint uses Form data.** The `/api/tts` endpoint expects `Form(...)` parameters, not JSON. API consumers sending JSON will get a 422 validation error. This is inconsistent with the JSON-based design of other endpoints.

## Known Issues

1. **Nginx DNS cache stale after container restart.** When `gizmo-orchestrator` is restarted via `podman restart`, it may get a new IP. Nginx in `gizmo-ui` caches the old IP and returns 502 until `nginx -s reload` is run. Workaround: restart `gizmo-ui` after restarting orchestrator, or add `resolver 127.0.0.11 valid=10s;` to nginx config.

2. **Whisper container reports unhealthy.** `podman ps` shows `(unhealthy)` for `gizmo-whisper`, but the `/health` endpoint returns `OK`. Likely a healthcheck configuration mismatch (wrong port, path, or timeout in docker-compose.yml).

3. **VRAM headroom dangerously low.** With 22120 MiB used and only 1961 MiB free, parallel inference requests could trigger OOM. The `--parallel 4` setting compounds this risk.

## Skipped

- **Category 10 interactive UI tests** — Requires manual browser testing. Page load, API proxy, and WebSocket proxy verified programmatically. The following checks require a browser session:
  - Sidebar navigation and conversation switching
  - Markdown rendering and code highlighting
  - Thinking block expand/collapse
  - Stop generation button
  - File upload preview
  - Microphone recording
  - Settings panel
  - TTS audio playback

## Fixes Applied During Audit

- **Nginx reload** — Ran `nginx -s reload` in `gizmo-ui` container to clear stale DNS cache after orchestrator restart. This is a temporary fix; the nginx config should be updated to avoid DNS caching.

## Recommendations for V2

1. **Fix vision:** Add `--mmproj /models/mmproj/Huihui-Qwen3.5-27B-abliterated.mmproj-Q8_0.gguf` to the llama.cpp container command in `docker-compose.yml`. Note: this will increase VRAM usage — may require reducing quant to Q4_K_M or lowering context size.

2. **Handle thinking mode properly:** Since the model always thinks, the orchestrator should filter out `reasoning_content` when `thinking=false` instead of relying on llama.cpp's `enable_thinking` parameter. This way the UI toggle actually works from the user's perspective.

3. **Add nginx DNS resolver:** Add `resolver 127.0.0.11 valid=10s;` to the nginx server block to prevent stale DNS after container restarts.

4. **Fix Whisper healthcheck:** Update the docker-compose healthcheck for whisper to match the actual health endpoint.

5. **Reduce VRAM pressure:** Lower `--parallel` from 4 to 2, or evaluate Q4_K_M quant which saves ~3GB VRAM with minimal quality loss.

6. **Set GitHub topics:** Add topics via `gh repo edit --add-topic ai,llm,local-ai,llama-cpp,self-hosted,qwen,voice-assistant`.

7. **Normalize API format:** Make `/api/tts` accept JSON instead of Form data for consistency.

8. **Add model info to orchestrator health:** Include model name and version in the `/health` response for operational visibility.
