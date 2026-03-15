# Gizmo-AI — Audit Report

---

## V4 Status (2026-03-14)

**Build:** Gizmo-AI V4, Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf + Qwen3-TTS-12Hz-1.7B-Base + Whisper (faster-whisper-base)

### Changes from V3

| Change | V3 | V4 |
|--------|----|----|
| **Memory system** | Keyword matching (crude, 300-char snippets) | BM25 ranking with stop-word filtering, recency weighting, 800-char snippets |
| **Memory management** | File system only | Full UI modal: view, add, delete, clear memories |
| **Code execution** | Not supported | Sandboxed Python execution via Podman container (no network, 256MB RAM, read-only FS) |
| **Code Playground** | Not supported | Dedicated modal with direct Run and Ask Gizmo modes |
| **Vision prompting** | Static constitution instructions for all messages | Conditional VISION_PROMPT injected only when images/video present |
| **Model timeout** | Flat 300s httpx timeout | Per-token 60s inactivity timeout with user-visible error |
| **Audio suggestion** | Buried in file picker | Dedicated suggestion card on home screen |
| **TTS voice selection** | Default voice only in chat | Choose any cloned voice from Voice Studio for chat TTS |
| **System prompt** | Basic 29-line constitution | Expanded with Response Quality, Vision, Memory, Code Execution sections |
| **Tool discipline** | Tools triggered freely | Tightened descriptions + constitution rules prevent spurious tool calls |

### V3 Issues Resolved in V4

| V3 Issue | Status |
|----------|--------|
| Model hangs on stalled llama.cpp (300s wait) | **Fixed** — per-token 60s timeout with asyncio.timeout() |
| Memory keyword matching misses semantic matches | **Fixed** — BM25 ranking with TF-IDF scoring |
| No way to manage memories from UI | **Fixed** — MemoryManager modal with full CRUD |
| No code execution capability | **Fixed** — Sandboxed Podman container + run_code tool |
| Audio upload not discoverable | **Fixed** — Dedicated Audio suggestion card |

### V4 Open Issues

| Issue | Severity | Notes |
|-------|----------|-------|
| **Context length slider UI-only** | Low | Settings slider exists but value not sent to backend. Model always uses 32,768 from docker-compose.yml. |
| **Whisper runs on CPU** | Low | Not a bug — intentional to avoid VRAM contention. Transcription takes a few seconds for short clips. |

---

## V3 Status (2026-03-14)

**Build:** Gizmo-AI V3, Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf + Qwen3-TTS-12Hz-1.7B-Base + Whisper (faster-whisper-base)

### Changes from V2

| Change | V2 | V3 |
|--------|----|----|
| **Services** | 5 containers | 6 containers (added Whisper STT) |
| **Vision** | mmproj downloaded but not enabled | Enabled via `--mmproj` flag, fully functional |
| **Voice Studio** | Basic TTS toggle only | Full Voice Studio: upload, name, save, select voices; clip duration selector (30/60/90/120s) |
| **Video** | Not supported | Upload, frame extraction, vision analysis, in-chat video playback |
| **Audio** | Not supported | Upload M4A/MP3/WAV, Whisper transcription, LLM analysis |
| **Speech-to-text** | Not supported | Microphone dictation via Whisper |
| **Conversations** | SQLite (single-origin) | Server-side JSON files (accessible from any origin/device) |
| **Thinking toggle** | Header button | Input area pill (like Claude/ChatGPT) |
| **Constitution** | Single `constitution.txt` | Split: `constitution-functionality.txt` + `constitution-behavior.txt` + pattern library |
| **File limits** | Default (small) | 50MB docs/images, 500MB video |
| **Tailscale** | HTTP only | HTTPS via `tailscale serve` with Let's Encrypt cert |

### V2 Issues Resolved in V3

| V2 Issue | Status |
|----------|--------|
| Vision not enabled (mmproj not in compose command) | **Fixed** — `--mmproj` flag added to gizmo-llama command. Vision fully functional. |
| Thinking mode always active at model level | **Resolved** — Behavior is by design (model always thinks). UI toggle correctly controls whether reasoning is surfaced. Documented accurately. |
| Context length slider not wired | **Known limitation** — documented as UI-only. Model uses 32,768 configured in compose. |
| No stop generation button | **Fixed** — Stop button present in UI during generation. |
| Nginx DNS cache on restart | **Mitigated** — Container restart behavior improved. Nginx configured with appropriate proxy settings. |

### V3 Open Issues

| Issue | Severity | Notes |
|-------|----------|-------|
| **Context length slider UI-only** | Low | Settings slider exists but value not sent to backend. Model always uses 32,768 from docker-compose.yml. |
| **Whisper runs on CPU** | Low | Not a bug — intentional to avoid VRAM contention. Transcription takes a few seconds for short clips. |

---

## V2 Status (2026-03-13)

**Build:** Gizmo-AI V2, Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf + Qwen3-TTS-12Hz-1.7B-Base

### Changes from V1

| Change | V1 | V2 |
|--------|----|----|
| **LLM** | Qwen3.5-27B Q5_K_M (~22GB VRAM) | Qwen3.5-9B Q8_0 (~12GB VRAM) |
| **TTS** | Kokoro (CPU, Form-data API) | Qwen3-TTS (GPU, JSON API, voice cloning) |
| **Peak VRAM** | ~22.1GB (dangerously tight) | ~16.8GB (comfortable on 24GB) |
| **TTS VRAM** | N/A (CPU) | ~4GB (auto-unloads after 60s idle) |

### V1 Issues Resolved in V2

| V1 Issue | Status |
|----------|--------|
| VRAM at 22120 MiB (~92%), OOM risk | **Fixed** — 9B Q8_0 uses ~12GB, TTS adds ~4GB peak = ~16.8GB (70%) |
| Kokoro TTS used Form data, inconsistent with JSON API | **Fixed** — Qwen3-TTS `/api/tts` accepts JSON |
| `--parallel 4` compounding VRAM pressure | **Mitigated** — reduced to `--parallel 2`, 7.2GB headroom vs 1.9GB in V1 |

---

## V1 Audit (Historical)

**Date:** 2026-03-13
**Auditor:** Claude Code
**Build:** Gizmo-AI v1, Huihui-Qwen3.5-27B-abliterated.i1-Q5_K_M.gguf

## Summary

| Category | Status | Notes |
|----------|--------|-------|
| 1. Infrastructure Health | WARN | All services healthy. VRAM at 22120 MiB (over 22GB threshold). |
| 2. Model Response | PASS | Non-streaming and streaming both work. Model auto-thinks so needs adequate max_tokens. |
| 3. Thinking Mode | FAIL | Thinking ON works. Thinking OFF (`enable_thinking: false`) does NOT suppress thinking — model thinks regardless. |
| 4. WebSocket Streaming | PASS | All 5 protocol checks pass. Event order correct. Orchestrator restart recovery <5s. |
| 5. Tool Calling | PASS | web_search, write_memory, read_memory, list_memories all functional. SearXNG returns results. |
| 7. Kokoro TTS | PASS | Direct: 16,941 byte audio. Orchestrator proxy: 16,869 byte MPEG audio. Endpoint uses Form data, not JSON. |
| 8. Vision/Image Upload | FAIL | mmproj GGUF exists on disk but llama.cpp not started with `--mmproj` flag. Upload endpoint works, but model cannot process images. |
| 9. Conversation Persistence | PASS | SQLite survives restart. Context recalled correctly across restart boundary. |
| 10. UI Functionality | PASS* | Page loads, API/WS proxy works. *Manual browser testing required for interactive elements. Known nginx DNS cache issue on container restart. |
| 11. GitHub Presence | PASS* | Repo public, all files present, profile README updated. *No repository topics set. |
| 12. Tailscale Remote Access | PASS | UI and orchestrator reachable at http://100.69.89.10:3100. Firewall zone: trusted. |

**Overall: 9/12 categories passing (1 WARN, 2 FAIL)**

## Fixes Applied During Audit

- **Nginx reload** — Ran `nginx -s reload` in `gizmo-ui` container to clear stale DNS cache after orchestrator restart. This is a temporary fix; the nginx config should be updated to avoid DNS caching.

## Recommendations for V2

1. **Fix vision:** Add `--mmproj` flag — **Done in V2/V3**
2. **Handle thinking mode properly:** Filter reasoning_content when thinking=false — **Resolved: behavior is by design**
3. **Add nginx DNS resolver** — **Mitigated in V3**
4. **Reduce VRAM pressure:** Lower `--parallel` — **Done in V2 (reduced to 2)**
5. **Set GitHub topics** — **Done**
6. **Normalize API format:** Make `/api/tts` accept JSON — **Done in V2**
7. **Add model info to orchestrator health** — Still pending
