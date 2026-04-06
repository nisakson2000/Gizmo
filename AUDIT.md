# Gizmo-AI — Audit Report

**Original Date:** 2026-03-25
**Updated:** 2026-04-06
**Auditor:** Claude Code
**Build:** Gizmo-AI V5.7, Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf + Qwen3-TTS-12Hz-1.7B-Base + Whisper (faster-whisper-base)
**Scope:** Full codebase review — orchestrator (main.py, llm.py, tracker.py, tracker_db.py, tracker_tools.py, memory.py, tools.py, sandbox.py, search.py, tts.py, code_chat.py), all UI components (.svelte, .ts), docker-compose, documentation

---

## Summary

| Severity | Open | Resolved |
|----------|------|----------|
| Critical | 0 | 5 |
| Warning | 0 | 17 |
| Suggestion | 4 | 6 |
| **Total** | **4** | **28** |

All critical and warning issues from the V5.1 audit have been resolved.

---

## Resolved Issues — Critical

| # | Issue | Location | Resolution |
|---|-------|----------|------------|
| C1 | Tracker endpoints may reject JSON bodies | `tracker.py` | Fixed — switched to `request: Request` + `await request.json()` pattern |
| C2 | `openFilePicker` drops the selected file | `ConsoleButtons.svelte` | Fixed — dispatches `gizmo:upload` custom event, `ChatInput.svelte` listens and triggers file upload |
| C3 | Concurrent `loadTasks()`/`loadNotes()` race on `tool_result` | `tracker-client.ts` | Fixed — refresh moved to `done` event only, not per `tool_result` |
| C4 | Tag LIKE search false-positive matches | `tracker_db.py` | Fixed — uses `json_each()`: `EXISTS (SELECT 1 FROM json_each(tags) WHERE value = ?)` |
| C5 | `JSON.parse` on persisted localStorage with no try/catch | `settings.ts`, `tracker.ts` | Fixed — both `persistedWritable` implementations wrap `JSON.parse` in try/catch |

---

## Resolved Issues — Warning (Backend)

| # | Issue | Location | Resolution |
|---|-------|----------|------------|
| W1 | `LLAMA_URL` imported then shadowed | `main.py` | Fixed — removed duplicate import, only `stream_chat` imported from `llm.py` |
| W2 | `arguments.pop()` mutates caller's dict | `tracker_tools.py` | Low risk — fresh `json.loads()` per call; no shared reference |
| W3 | Synchronous SQLite on async WebSocket | `tracker.py` | Fixed — `_build_context_summary()` uses `asyncio.to_thread()` |
| W4 | `delete_task` orphans grandchild tasks | `tracker_db.py` | Fixed — recursive CTE deletes all descendants |
| W5 | `complete_task` recurrence is non-atomic | `tracker_db.py` | Fixed — single connection and `conn.commit()` for both UPDATE + INSERT |
| W6 | Partial response saved to history on stream error | `tracker.py`, `code_chat.py` | Fixed — `stream_errored` flag prevents saving corrupt history |

## Resolved Issues — Warning (UI)

| # | Issue | Location | Resolution |
|---|-------|----------|------------|
| W7 | All tracker API errors silently swallowed | `tracker.ts` | Fixed — catch blocks call `toast()` with error messages |
| W8 | `$effect` sync clobbers in-progress edits | `TaskDetail.svelte`, `NoteEditor.svelte` | Fixed — `lastSyncedId` pattern only syncs when selected ID changes |
| W9 | Sort `<select>` not bound to store value | `TaskList.svelte` | Fixed — `value={$taskFilter.sort}` binding present |
| W10 | `connectTracker()` doesn't cancel pending reconnect | `tracker-client.ts` | Fixed — `clearTimeout(reconnectTimeout)` at top of `connectTracker()` |
| W11 | `reconnectDelay` never reset | `tracker-client.ts` | Fixed — `reconnectDelay = 1000` in `disconnectTracker()` |
| W12 | `replayBoot` in ConsoleButtons is fragile | `ConsoleButtons.svelte` | Fixed — uses `gizmo:replay-boot` custom event dispatched to `BootSequence` |
| W13 | `playCancel` imported but never called | `ChatInput.svelte` | Fixed — dead import removed, only `playSelect` imported |
| W14 | `playBootSound` bypasses `soundsEnabled` guard | `sounds.ts` | Fixed — `if (!get(soundsEnabled)) return;` guard added |
| W15 | `disconnectTracker` doesn't reset streaming state | `tracker-client.ts` | Fixed — finalizes message and clears streaming stores on disconnect |
| W16 | BootSequence dismiss `setTimeout` not tracked | `BootSequence.svelte` | Fixed — cleanup `$effect` clears both `skipTimeout` and `fadeTimeout` |

## Resolved Issues — Warning (Data)

| # | Issue | Location | Resolution |
|---|-------|----------|------------|
| W17 | `loadConversation` drops variant/media state | `chat.ts` | Accepted — variants are session-only state, not DB-persisted by design |

---

## Resolved Issues — Suggestions

| # | Issue | Location | Resolution |
|---|-------|----------|------------|
| S1 | Cache `_load_tracker_prompt()` | `tracker.py` | Fixed — mtime-checked cache, reads disk only when file changes |
| S2 | `_build_context_summary` truncates mid-line | `tracker.py` | Fixed — `rsplit("\n", 1)[0]` truncates at line boundary |
| S3 | `list_notes()` fetches all then slices in Python | `tracker.py`, `tracker_db.py` | Fixed — `LIMIT` clause in SQL query |
| S8 | `_new_id()` uses 8 hex chars (32 bits) | `tracker_db.py` | Fixed — increased to 12 hex chars (48 bits) |
| S9 | CLAUDE.md Future Roadmap stale | `CLAUDE.md` | Reviewed — roadmap items are genuine future plans, not stale |
| S10 | VRAM docs inconsistent | `wiki/setup.md` vs `CLAUDE.md` | Documented — different figures reflect different measurement points (peak vs steady-state) |

---

## Open Suggestions

| # | Issue | Location | Description |
|---|-------|----------|-------------|
| S4 | Google Fonts loaded from CDN | `themes.css` | Self-hosted app depends on external CDN. Fonts unavailable on isolated LAN. Self-host in `static/fonts/`. |
| S5 | Console sounds toggle missing `aria-label` | `Settings.svelte` | `role="switch"` has no label for screen readers. Add `aria-label="Toggle console sounds"`. |
| S6 | Sidebar mobile overlay has no keyboard dismiss | `Sidebar.svelte` | Escape doesn't close sidebar on mobile. Add `sidebarOpen` to the layout's Escape handler chain. |
| S7 | No unsaved-changes guard in TaskDetail/NoteEditor | `TaskDetail.svelte`, `NoteEditor.svelte` | Auto-save mitigates data loss, but clicking another task/note during the 800ms debounce window can discard edits. Low risk due to `onDestroy` save. |

---

## Additional Findings (2026-04-06 review)

These items were identified during the V5.6 comprehensive code review and fixed in the same session:

| # | Issue | Location | Severity | Description | Fix |
|---|-------|----------|----------|-------------|-----|
| N1 | Code injection in `_build_doc_code()` | `tools.py:151-248` | Warning | Triple-quote breakout possible when title/content contained `'''`. Sandbox isolation limited blast radius but was still a code injection vector. | Replaced string interpolation with base64 encoding. Title/content decoded safely inside sandbox. |
| N2 | SQL LIKE metacharacter not escaped in search | `main.py:926-940` | Low | Searching for `%` or `_` matched unintended rows due to LIKE wildcards. Not a security issue (parameterized queries prevent SQL injection) but a correctness bug. | Added `ESCAPE '\\'` clause and escaping of `%`, `_`, `\\` in search term. |
| N3 | `settings.ts` `persistedWritable` missing try/catch | `settings.ts:3-10` | Warning | Identical to C5 but in settings store (tracker store was already fixed). Corrupt localStorage crashed the page. | Added try/catch with fallback to defaultValue. |
