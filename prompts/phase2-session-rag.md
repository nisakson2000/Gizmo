# Phase 2: Session History RAG — Coder Prompt

You are implementing session-level semantic recall for Gizmo-AI. Read CLAUDE.md first for full project context.

## Problem

Gizmo's conversation history is managed by a sliding window (`window_messages()` in main.py) that drops the oldest messages first when the context budget is exceeded. Once a message scrolls out of the window, it's gone — the model has no way to recall it. If a user discusses Python in messages 1-10, then switches topics for 20 messages, asking "what did we discuss about Python earlier?" returns nothing useful because those messages were dropped.

## Solution

Embed every conversation turn using a lightweight CPU-based embedding model. Store vectors in SQLite alongside existing conversation data. On each new message, if the conversation is long enough that older messages would be dropped, retrieve the most semantically relevant earlier turns by cosine similarity and inject them into the system prompt. The model then has access to relevant earlier context even when it's been pushed out of the sliding window.

## Key Design Decisions

- **fastembed over sentence-transformers**: fastembed uses ONNX Runtime (~200-300MB installed). sentence-transformers pulls in PyTorch (~2-3GB). The orchestrator Dockerfile is python:3.12-slim — we cannot balloon the image.
- **Embedding model**: `BAAI/bge-small-en-v1.5` via fastembed — 384-dim vectors, ~33MB model, runs on CPU. Zero VRAM impact.
- **Storage**: SQLite table in the existing conversations.db (not a separate database).
- **Injection point**: New `<session-recall>` XML block in the system prompt, between recitation and vision layers.
- **Background indexing**: Embedding happens after saving the assistant response, as a fire-and-forget background task — never blocks the response stream.
- **Retrieval threshold**: Only retrieve when conversation has 15+ messages (shorter conversations fit in the window anyway).
- **Exclude recent turns**: Don't retrieve the last 10 messages — they're already in the sliding window.

## What to Build

### 1. Create `services/orchestrator/session_memory.py` (~160 lines)

This module handles embedding, storage, and retrieval of conversation turn vectors.

**Lazy singleton embedder:**
```python
import logging
import numpy as np
from pathlib import Path

logger = logging.getLogger(__name__)

_embedder = None

def _get_embedder():
    """Lazy-load the fastembed embedding model (first call downloads ~33MB)."""
    global _embedder
    if _embedder is None:
        from fastembed import TextEmbedding
        _embedder = TextEmbedding("BAAI/bge-small-en-v1.5")
        logger.info("Embedding model loaded: BAAI/bge-small-en-v1.5")
    return _embedder
```

**`def embed_text(text: str) -> bytes`**
- Call `_get_embedder()`, embed the text, convert the 384-dim float32 numpy array to bytes via `.tobytes()`
- fastembed's `embed()` returns a generator of numpy arrays — call `list(embedder.embed([text]))[0]`
- Return the raw bytes (1,536 bytes per vector: 384 dims × 4 bytes)

**`def store_turn(conversation_id: str, message_index: int, role: str, content: str)`**
- Import `get_db` from `main` (or use a direct sqlite3 connection to the same DB_PATH)
- Truncate content to first 2,000 chars before embedding (longer content doesn't improve similarity matching and wastes compute)
- Call `embed_text(content[:2000])` to get the embedding bytes
- INSERT OR REPLACE into `session_embeddings` table
- Wrap in try/except — embedding failures must never crash the main flow
- Log at debug level

**`def retrieve_relevant(conversation_id: str, query: str, top_k: int = 5, exclude_recent: int = 10) -> list[dict]`**
- Embed the query text
- Load all embeddings for this conversation from SQLite: `SELECT message_index, role, content, embedding FROM session_embeddings WHERE conversation_id = ? ORDER BY message_index`
- If fewer than `exclude_recent` rows exist, return empty list (nothing to recall)
- Exclude the last `exclude_recent` entries (they're in the sliding window)
- Compute cosine similarity between query embedding and each stored embedding:
  ```python
  def _cosine_sim(a: np.ndarray, b: np.ndarray) -> float:
      dot = np.dot(a, b)
      norm = np.linalg.norm(a) * np.linalg.norm(b)
      return float(dot / norm) if norm > 0 else 0.0
  ```
- Convert each stored BLOB back to numpy: `np.frombuffer(row["embedding"], dtype=np.float32)`
- Filter results with similarity > 0.3 (below this threshold, results are noise)
- Sort by similarity descending, take top_k
- Return list of dicts: `[{"message_index": int, "role": str, "content": str, "similarity": float}, ...]`

**`def format_recalled(turns: list[dict]) -> str`**
- If turns is empty, return `""`
- Format as XML block:
  ```
  <session-recall>
  Relevant earlier messages from this conversation (retrieved by semantic similarity
  because they may be relevant to the current question):

  [Turn {message_index}] {Role}: {content first 600 chars}
  [Turn {message_index}] {Role}: {content first 600 chars}
  ...

  Use this recalled context to answer questions about earlier parts of the conversation.
  If the user asks about something not found in these recalled messages or the recent
  conversation history, say you don't have that context available rather than guessing.
  </session-recall>
  ```
- Truncate each turn's content to 600 chars in the output (keep the full content in the DB, only truncate for prompt injection to save tokens)

**Database path**: Use the same DB_PATH as main.py. Import it or reconstruct it:
```python
DB_PATH = Path("/app/memory/conversations.db")
```

### 2. Modify `services/orchestrator/main.py`

**init_db() (line 125-161):** Add the session_embeddings table. After the existing migrations (after line 158), add:

```python
conn.execute("""
    CREATE TABLE IF NOT EXISTS session_embeddings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        conversation_id TEXT NOT NULL,
        message_index INTEGER NOT NULL,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        embedding BLOB NOT NULL,
        created_at TIMESTAMP,
        UNIQUE(conversation_id, message_index)
    )
""")
# Index for fast lookup by conversation
conn.execute("""
    CREATE INDEX IF NOT EXISTS idx_session_embeddings_conv
    ON session_embeddings(conversation_id)
""")
```

**build_system_prompt() (line 255-279):** Add `session_recall: str = ""` parameter. Inject between recitation and vision layers:

```python
def build_system_prompt(user_message: str = "", has_vision: bool = False,
                        pattern: dict | None = None,
                        recitation_context: str = "",
                        session_recall: str = "") -> str:
```

Add after the recitation block and before the vision block:
```python
    if session_recall:
        parts.append(f"\n\n{session_recall}")
```

**ws_chat() (~line 570-587):** After routing and recitation, before building the system prompt, add session recall retrieval:

After `recitation_context, llm_temperature = await prepare_recitation(...)` (line 576) and before `system_prompt = build_system_prompt(...)` (line 580):

```python
# Session recall — retrieve relevant earlier turns if conversation is long
session_recall = ""
if len(history_msgs) > 15:
    try:
        from session_memory import retrieve_relevant, format_recalled
        recalled = retrieve_relevant(conversation_id, user_text)
        if recalled:
            session_recall = format_recalled(recalled)
            conv_log.info("[%s] Session recall: %d turns retrieved", trace_id, len(recalled))
    except Exception as e:
        error_log.debug("Session recall failed: %s", e)
```

Pass to build_system_prompt:
```python
system_prompt = build_system_prompt(
    clean_text,
    has_vision=has_vision,
    pattern=route_result.pattern,
    recitation_context=recitation_context,
    session_recall=session_recall,
)
```

**ws_chat() (~line 718-722, after saving assistant response):** After the assistant's `save_message()` call, add background embedding of both the user message and assistant response:

```python
# Background: embed conversation turns for session recall
if full_response:
    save_message(conversation_id, "assistant", full_response, full_thinking,
                 audio_url=audio_file_url,
                 tool_calls=executed_tool_calls if executed_tool_calls else None)
    conv_log.info("[%s] ASSISTANT (%s): %s", trace_id, conversation_id, full_response[:500])

    # Index turns for session recall (fire-and-forget, never blocks response)
    try:
        from session_memory import store_turn
        msg_count = len(get_conversation_messages(conversation_id))
        # Embed user message (index = count - 2) and assistant message (index = count - 1)
        asyncio.create_task(asyncio.to_thread(store_turn, conversation_id, msg_count - 2, "user", user_text))
        asyncio.create_task(asyncio.to_thread(store_turn, conversation_id, msg_count - 1, "assistant", full_response))
    except Exception:
        pass  # Embedding failure must never affect the main flow
```

Note: `store_turn` is synchronous (SQLite + numpy). Wrapping in `asyncio.to_thread` keeps it off the event loop. `asyncio.create_task` makes it fire-and-forget.

**rest_chat() (~line 758-830):** Same two changes:
1. Add session recall retrieval after recitation and before build_system_prompt
2. Add background embedding after saving the assistant response

For rest_chat, the retrieval follows the same pattern. The embedding can use the same `asyncio.create_task(asyncio.to_thread(...))` pattern since rest_chat is also async.

### 3. Modify `services/orchestrator/requirements.txt`

Add two lines:
```
fastembed>=0.4.0
numpy>=1.26.0
```

### 4. Modify `docker-compose.yml`

Add a cache volume mount to the `gizmo-orchestrator` service so the fastembed model is downloaded once and persists across container rebuilds/restarts.

In the orchestrator's `volumes:` section (after line 116), add:
```yaml
      - ./memory/.fastembed-cache:/root/.cache/fastembed:rw,Z
```

Note: fastembed defaults to `~/.cache/fastembed` which is `/root/.cache/fastembed` inside the container (the Dockerfile CMD runs as root). By mounting this, the ~33MB model download happens only once.

### 5. Modify `services/orchestrator/Dockerfile` — probably no changes needed

fastembed and onnxruntime install cleanly on python:3.12-slim via pip. If the build fails due to missing system libs, add them to the apt-get line. But this should not be necessary.

## What NOT to Do

- Do NOT modify the existing BM25 memory system (memory.py). Session recall is a separate, complementary system.
- Do NOT modify the UI. This is backend-only.
- Do NOT add any GPU/VRAM-consuming dependencies. fastembed uses CPU only.
- Do NOT make embedding synchronous in the request path. Always use `asyncio.to_thread` + `asyncio.create_task` for indexing.
- Do NOT add a new REST endpoint for session recall. It integrates into the existing chat flow.
- Do NOT change the recitation pipeline (Phase 1). Session recall is a new layer, not a replacement.
- Do NOT embed messages at retrieval time that haven't been stored yet. Only embed after save_message().
- Do NOT use sentence-transformers or PyTorch. Use fastembed (ONNX-based).

## Build, Test, and Iterate

**You are responsible for testing.** After writing the code, you MUST rebuild, deploy, and run every test below yourself. Do not just list the commands — execute them. Check each response for correctness. If a test fails, debug using `podman logs gizmo-orchestrator --tail 50`, fix the code, rebuild, and retest. Do not move on to documentation until all tests pass.

### Step 1: Rebuild and deploy

The image will be larger due to fastembed/onnxruntime (~200-300MB). Build time will increase.

```bash
podman compose build gizmo-orchestrator && podman compose up -d gizmo-orchestrator
```

Wait for health check (first startup may be slower due to model download):
```bash
sleep 15 && curl -s http://localhost:9100/health
```

Check logs for successful embedding model load:
```bash
podman logs gizmo-orchestrator --tail 20 | grep -i embed
```

### Step 2: Seed a long conversation

Create a conversation with 15+ exchanges across multiple topics. Use a script or run these sequentially, saving the conversation_id from the first response:

```bash
# Start conversation — save the conversation_id from this response
RESPONSE=$(curl -s -X POST http://localhost:9100/api/chat \
  -F "message=I'm working on a Python project that uses FastAPI with WebSocket streaming. The key challenge is managing BM25-ranked memory retrieval in the orchestrator layer." \
  -F "conversation_id=session-rag-test")
echo "$RESPONSE" | python3 -m json.tool

# Continue with different topics to push the first messages out of the window
for i in $(seq 1 8); do
  curl -s -X POST http://localhost:9100/api/chat \
    -F "message=Topic $i: Tell me something interesting about the number $i in mathematics." \
    -F "conversation_id=session-rag-test" > /dev/null
  echo "Sent message $i"
  sleep 2
done
```

### Step 3: Test recall of early content

```bash
# Test 1: Ask about early content that should have scrolled out of window
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What did I tell you earlier about my Python project?" \
  -F "conversation_id=session-rag-test" | python3 -m json.tool
```
**Expected:** Response should reference FastAPI, WebSocket streaming, BM25, and the orchestrator — details from the first message. Check orchestrator logs for "Session recall: N turns retrieved" to confirm the retrieval fired.

```bash
# Test 2: Ask about a specific detail
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What was the key challenge I mentioned in my project?" \
  -F "conversation_id=session-rag-test" | python3 -m json.tool
```
**Expected:** Should mention "managing BM25-ranked memory retrieval in the orchestrator layer."

```bash
# Test 3: No false recall in a fresh conversation
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What did we discuss earlier about databases?" | python3 -m json.tool
```
**Expected:** Should say nothing was discussed (new conversation, no history, no session recall injected).

```bash
# Test 4: Verify conversation isolation — different conversation_id should not leak
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What did I say about my Python project?" \
  -F "conversation_id=isolation-test" | python3 -m json.tool
```
**Expected:** Should NOT reference the Python project from the other conversation.

### Step 4: Test persistence across restart

```bash
# Restart orchestrator
podman compose restart gizmo-orchestrator
sleep 15

# Same recall test — embeddings should survive restart
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=Remind me about my Python project details" \
  -F "conversation_id=session-rag-test" | python3 -m json.tool
```
**Expected:** Same quality recall as before restart. Embeddings are in SQLite, not memory.

### Step 5: Verify no performance regression

```bash
# Quick conversation — should respond normally without noticeable delay
time curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What is 2 plus 2?" | python3 -m json.tool
```
**Expected:** Response time should not be significantly worse than before. The embedding model lazy-loads on first use, so the very first request after a fresh container start may take a few extra seconds.

### Step 6: If any test fails

1. Check logs: `podman logs gizmo-orchestrator --tail 50`
2. Check if the session_embeddings table was created: `podman exec gizmo-orchestrator python3 -c "import sqlite3; conn=sqlite3.connect('/app/memory/conversations.db'); print([r[1] for r in conn.execute('PRAGMA table_info(session_embeddings)').fetchall()])"`
3. Check if embeddings are being stored: `podman exec gizmo-orchestrator python3 -c "import sqlite3; conn=sqlite3.connect('/app/memory/conversations.db'); print(conn.execute('SELECT COUNT(*) FROM session_embeddings').fetchone())"`
4. Fix the issue, rebuild: `podman compose build gizmo-orchestrator && podman compose up -d gizmo-orchestrator`
5. Rerun the failing test
6. Repeat until all tests pass

**Do not proceed to documentation until all tests pass.**

## Documentation Updates (MANDATORY per CLAUDE.md)

After all tests pass, update these files:

1. **CLAUDE.md** — Add "Session History RAG" subsection under the Memory System section. Include: fastembed model name, vector dimensions, storage location (session_embeddings table in conversations.db), retrieval threshold (15+ messages, exclude recent 10, similarity > 0.3, top 5), injection point in system prompt. Update System Facts to note fastembed dependency. Update Session Log with version entry.
2. **README.md** — Update the "Memory system" bullet in the AI Capabilities section to mention session-level semantic recall alongside BM25. Add a note about zero VRAM impact (CPU-only embeddings).
3. **wiki/architecture.md** — Add `session_memory.py` to the file tree. Update the prompt assembly diagram to show the session_recall layer. Add the session_embeddings table to the database schema section.
4. **wiki/usage.md** — Add a section explaining session recall (automatic, no user action needed, works for conversations with 15+ messages).
5. **wiki/development.md** — Update the Future Roadmap section: "ChromaDB semantic memory" can be noted as partially addressed by session RAG (BM25 for cross-conversation, embeddings for within-conversation).
6. **AUDIT.md** — Add version entry header for this change.
7. **docker-compose.yml** — Already modified (volume mount), but verify the comment documents what the mount is for.
