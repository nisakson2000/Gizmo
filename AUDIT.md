# Gizmo-AI — Audit Report

**Date:** 2026-04-06
**Auditor:** Claude Code
**Build:** Gizmo-AI V5.8
**Scope:** Full codebase review — orchestrator (main.py, llm.py, tools.py, router.py, patterns.py, sandbox.py, tts.py, search.py, memory.py, tracker.py, tracker_db.py, tracker_tools.py, code_chat.py, recite.py, web_fetch.py), all UI components (.svelte, .ts), docker-compose.yml, Dockerfiles, constitution.txt, config files, all 30 pattern configs

---

## Summary

| Severity | Count |
|----------|-------|
| Warning  | 0     |
| Suggestion | 0   |
| **Total** | **0** |

---

## Resolved Issues (V5.8)

All issues from the V5.8 audit have been resolved.

### Warnings (fixed)

| ID | Issue | Fix |
|----|-------|-----|
| W1 | gizmo-searxng healthcheck has no start_period | Added `start_period: 30s` to docker-compose.yml |
| W2 | gizmo-orchestrator healthcheck has no start_period | Added `start_period: 10s` to docker-compose.yml |
| W3 | TTS failure is silent — no error sent to client | Added `else` branch sending `{"type": "error", "error": "TTS synthesis failed"}` to WebSocket |
| W4 | ffmpeg subprocess has no timeout in upload_video() | Added `timeout=30` to both `ffprobe` and `ffmpeg` `subprocess.run()` calls |
| W5 | stopGeneration() can create overlapping WebSocket connections | Null all handlers on old socket before closing, defer `connect()` to next tick via `setTimeout` |

### Suggestions (fixed)

| ID | Issue | Fix |
|----|-------|-----|
| S1 | DOMPurify SVG allow-list lacks explicit foreignObject block | Added `FORBID_TAGS: ['foreignObject', 'script', 'iframe']` to sanitize.ts |
| S2 | loadConversations() silently swallows fetch errors | Added toast notification on catch: "Could not load conversations — orchestrator may be down" |
| S3 | Upload fetch calls have no timeout | Added `AbortController` with 60s timeout to all 4 fetch calls in ChatInput.svelte (transcribe, upload-video/image, audio file upload, mic recording) |
| S4 | Pattern cache globals have no synchronization primitive | Added `threading.Lock()` around all cache read/write paths in patterns.py |

---

## Verified Correct (False Positives Rejected)

The following claims from the automated sweep were investigated and found to be non-issues:

| Claim | Verdict | Reason |
|-------|---------|--------|
| XSS via SVG `foreignObject` | **Safe** | DOMPurify blocks `foreignObject` by default even with `ADD_TAGS: ['svg']` — hardened further in S1 |
| `recitation_context` parameter missing from `build_system_prompt()` | **False** | Parameter exists in the function signature at `main.py:254-256` |
| Audio double-finalize data corruption in `client.ts` | **False** | `audioFinalized` flag works correctly — set in `audio` handler, checked in `done` handler, reset on line 135 |
| Symlink traversal in `serve_media()` | **False** | `resolve()` + `is_relative_to()` at `main.py:952-954` catches symlinks — `resolve()` follows symlinks before the boundary check |
| Sandbox code injection via env vars | **False** | `printenv SOURCE_CODE > /tmp/file` outputs literal env var value — shell metacharacters in the value are not interpreted |
| Pattern cache race condition (CRITICAL) | **Overstated** | Eager loading at startup eliminates the race window — hardened with threading.Lock in S4 |
| SQL injection in tracker `LIMIT` clause | **False** | `int(limit)` cast at `tracker_db.py:358` prevents injection; unbounded limit is a style issue, not a vulnerability |
| `httpx.AsyncClient` resource leak in `llm.py` | **False** | Context manager (`async with`) guarantees cleanup on all exit paths including early returns |
| `generate_title()` silent failure | **False** | Wrapped in try/except at `main.py:422` with error logging — fire-and-forget is intentional for a non-critical background task |

---

## Previously Resolved Issues (V5.7)

All issues from the initial V5.7 audit were resolved in commits `cafb628` and prior.

### Warnings (fixed in cafb628)

| ID | Issue | Fix |
|----|-------|-----|
| W1 | Substring keyword matching caused false-positive pattern activation | Replaced with word-boundary regex (`re.search(r'\b' + re.escape(keyword) + r'\b')`) |
| W2 | REST `/api/chat` not integrated with pattern router | Added `route()` call and `pattern`/`tool_schemas` passthrough, mirroring WebSocket handler |
| W3 | Unhandled exception reading `system.md` could crash startup | Wrapped in try/except with `logger.warning()` and `continue` |

### Suggestions (fixed in cafb628 or already implemented)

| ID | Issue | Resolution |
|----|-------|------------|
| S1 | Keyword matching should use word-boundary regex | Fixed with W1 |
| S2 | Short/generic keywords cause false matches | Removed `vs`, `what is`, `how does`, `explain`; lengthened `edit this` → `edit this writing`, `not working` → `code not working`, etc. |
| S3 | `[pattern:name]` prefix sent to LLM and DB | Router now strips prefix, returns cleaned message; both WS and REST handlers use cleaned text |
| S4 | Google Fonts loaded from CDN | Already self-hosted in `static/fonts/` — was a false positive |
| S5 | Console sounds toggle missing `aria-label` | Already present: `aria-label="Toggle console sounds"` on `Settings.svelte:104` |
| S6 | Sidebar mobile overlay has no keyboard dismiss | Already handled: `+layout.svelte:29` global Escape handler chain includes `sidebarOpen` |
| S7 | No unsaved-changes guard in TaskDetail/NoteEditor | Already implemented: `onDestroy` handlers flush dirty state; 800ms debounce with `capturedTaskId`/`capturedNoteId` |

---

## Things That Are Correct

The following areas were audited and found to be clean:

- **All imports valid** — No circular imports, no missing modules across all orchestrator Python files and UI TypeScript/Svelte files
- **async/await correctness** — All async generators properly consumed, no missing awaits
- **Tool registry consistency** — All 6 TOOL_DEFINITIONS have matching TOOL_REGISTRY entries; all pattern config.yaml `tools` lists reference valid tool names only
- **Pattern loading** — 30 patterns load correctly, cache system works with threading.Lock, `list_patterns()` and `get_pattern()` return expected data
- **Router three-step logic** — Recitation detection (Step 0), keyword pre-routing, pattern matching, and default fallback work correctly; tool sets properly merge via set union
- **Tool scoping** — `get_default_tools()` returns always_available tools; patterns add their scoped tools on top; no tool is accidentally excluded
- **WebSocket protocol** — All message types properly handled in all three WS clients (chat, code, tracker)
- **ToolCallBlock.svelte** — Correct Svelte 5 $props usage, media detection works, copy-to-clipboard functional
- **Constitution** — `<patterns>` section properly instructs the LLM to follow active patterns
- **Docker/Podman** — All volume mounts include `:Z` for SELinux, all containers have healthchecks with start_period, all have restart policies, all ports unique and documented
- **VRAM safety** — No new GPU consumers added; pattern system and recitation pipeline are CPU-only
- **Security** — Pattern system prompts loaded from read-only mount; `[pattern:name]` lookup is cache-key only (no path traversal); no user input reaches filesystem paths; serve_media uses resolve() + is_relative_to(); sandbox has network disabled, read-only rootfs, memory/CPU/PID limits; DOMPurify explicitly forbids foreignObject/script/iframe
- **Code chat and tracker isolation** — Intentionally do NOT use the pattern router; dedicated tool sets by design
- **Multi-round tool loop** — Both WebSocket and continuation streams pass `route_result.tool_schemas` and temperature consistently
- **persistedWritable** — Clean dedup into `persisted.ts`, all consumers properly import, try/catch on JSON.parse present
- **LIKE escaping** — Both `main.py:963` and `tracker_db.py:351-353` properly escape SQL LIKE metacharacters
- **Sandbox execution** — Proper isolation (read-only rootfs, no network, 256MB memory, 1 CPU, 256 PID limit, tmpfs /tmp)
- **Tool argument validation** — `execute_tool()` uses explicit function name whitelist, no dynamic dispatch
- **No hardcoded secrets** — No credentials, API keys, or tokens in any source or config file
- **Recitation pipeline** — Pre-LLM interception works correctly; graceful fallback when SearXNG is down; temperature lowered to 0.2 for faithful reproduction; no VRAM impact
