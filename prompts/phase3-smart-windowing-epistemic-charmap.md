# Phase 3: Smart Windowing + Anti-Confabulation + Character Analysis — Coder Prompt

You are implementing the final phase of memory/recall improvements for Gizmo-AI. Read CLAUDE.md first for full project context.

This phase has three independent parts that share no code dependencies between them. Implement all three.

## Context from Prior Phases

Phase 1 added a recitation pipeline (recite.py, web_fetch.py) that intercepts requests for known texts, fetches them from the web, and injects the authoritative content into the system prompt at low temperature.

Phase 2 added session-level semantic recall (session_memory.py) using fastembed embeddings stored in SQLite. Every conversation turn is embedded and stored. For long conversations (15+ messages), relevant earlier turns are retrieved by cosine similarity and injected into the system prompt as `<session-recall>` context.

The system prompt is now assembled in this order:
```
constitution → pattern → recitation → session_recall → vision → memories
```

## Part A: Smart History Windowing

### Problem

`window_messages()` in main.py (line 377-403) drops the oldest messages first when the context budget is exceeded. If a user discusses Python in messages 1-10, then switches topics for 30 messages, the Python messages are gone from the conversation history — even if the user is asking about Python right now.

Session recall (Phase 2) partially addresses this by injecting relevant earlier content into the system prompt, but that content appears as a "note" in the system prompt rather than as actual conversation messages. Messages in the conversation history carry proper role markers and chronological flow, which matters for multi-turn reasoning.

### Solution

Modify `window_messages()` to accept an optional query embedding. When provided:
1. Always keep the last 6 messages (3 user-assistant pairs) — recency matters most
2. For all older messages, look up their stored embeddings from the session_embeddings table
3. Score each older message by cosine similarity to the query
4. Fill the remaining token budget with the highest-scoring older messages
5. Merge all kept messages in chronological order

When the query embedding is not provided (or the conversation is short), fall back to the current FIFO behavior.

### Implementation

**Modify `services/orchestrator/session_memory.py`** — add a helper to get a query embedding:

```python
def get_query_embedding(text: str) -> bytes | None:
    """Embed a query for smart windowing. Returns None if embedding fails."""
    try:
        return embed_text(text[:2000])
    except Exception:
        return None
```

Also add a helper to load stored embeddings for a conversation:

```python
def get_stored_embeddings(conversation_id: str) -> dict[int, bytes]:
    """Load all stored embeddings for a conversation as {message_index: embedding_bytes}."""
    try:
        conn = sqlite3.connect(str(DB_PATH))
        try:
            rows = conn.execute(
                "SELECT message_index, embedding FROM session_embeddings WHERE conversation_id = ?",
                (conversation_id,),
            ).fetchall()
        finally:
            conn.close()
        return {r[0]: r[1] for r in rows}
    except Exception:
        return {}
```

**Modify `services/orchestrator/main.py`** — update `window_messages()` (line 377-403):

Add two optional parameters: `query_embedding: bytes | None = None` and `conversation_id: str = ""`.

New algorithm when both are provided and history has > 6 messages:

```python
def window_messages(history: list[dict], system_prompt: str, context_length: int,
                    query_embedding: bytes | None = None,
                    conversation_id: str = "") -> list[dict]:
    """Trim conversation history to fit within the token budget.

    When query_embedding is provided, uses semantic scoring to keep the most
    relevant older messages instead of just the most recent ones.
    Falls back to FIFO (drop oldest) when embeddings are unavailable.
    """
    system_tokens = estimate_tokens(system_prompt)
    response_reserve = MAX_RESPONSE_TOKENS + 256
    budget = context_length - system_tokens - response_reserve

    if budget <= 0 or not history:
        return history[-1:] if history else []

    # --- Smart windowing (semantic scoring) ---
    if query_embedding and conversation_id and len(history) > 6:
        try:
            from session_memory import get_stored_embeddings, _cosine_sim
            import numpy as np

            recent_count = min(6, len(history))
            recent = history[-recent_count:]
            recent_tokens = sum(estimate_tokens(m.get("content", "")) for m in recent)

            older_budget = budget - recent_tokens
            if older_budget <= 0:
                return recent

            older = history[:-recent_count]
            emb_lookup = get_stored_embeddings(conversation_id)
            query_vec = np.frombuffer(query_embedding, dtype=np.float32)

            scored = []
            for i, msg in enumerate(older):
                tokens = estimate_tokens(msg.get("content", ""))
                emb_bytes = emb_lookup.get(i)
                if emb_bytes:
                    stored_vec = np.frombuffer(emb_bytes, dtype=np.float32)
                    sim = _cosine_sim(query_vec, stored_vec)
                else:
                    sim = 0.0  # No stored embedding — lowest priority
                scored.append((sim, i, msg, tokens))

            # Sort by similarity descending, greedily fill budget
            scored.sort(key=lambda x: x[0], reverse=True)
            kept_older = []
            used = 0
            for sim, idx, msg, tokens in scored:
                if used + tokens > older_budget:
                    continue
                kept_older.append((idx, msg))
                used += tokens

            # Restore chronological order
            kept_older.sort(key=lambda x: x[0])
            return [msg for _, msg in kept_older] + recent

        except Exception:
            pass  # Fall through to FIFO

    # --- FIFO fallback (drop oldest) ---
    kept: list[dict] = []
    used = 0
    for msg in reversed(history):
        msg_tokens = estimate_tokens(msg.get("content", ""))
        if used + msg_tokens > budget and kept:
            break
        kept.append(msg)
        used += msg_tokens

    kept.reverse()
    return kept
```

**Modify ws_chat() (~line 637-649):** After `prepare_session_recall()` and before `window_messages()`, compute the query embedding and pass it:

```python
session_recall = await prepare_session_recall(
    conversation_id, user_text, len(history_msgs), f"[{trace_id}]")

# Compute query embedding for smart windowing (reuses Phase 2 infrastructure)
query_embedding = None
if len(history_msgs) > 6:
    try:
        from session_memory import get_query_embedding
        query_embedding = await asyncio.to_thread(get_query_embedding, user_text)
    except Exception:
        pass

# Build prompt with pattern (use cleaned text for memory retrieval)
has_vision = bool(image_data or video_frames)
system_prompt = build_system_prompt(
    clean_text,
    has_vision=has_vision,
    pattern=route_result.pattern,
    recitation_context=recitation_context,
    session_recall=session_recall,
)
history_msgs = window_messages(history_msgs, system_prompt, context_length,
                               query_embedding=query_embedding,
                               conversation_id=conversation_id)
messages = build_messages(history_msgs, system_prompt)
```

**Modify rest_chat() (~line 835-843):** Same pattern — compute query embedding and pass to window_messages:

```python
session_recall = await prepare_session_recall(
    conversation_id, clean_text, len(history_msgs), "[REST]")

query_embedding = None
if len(history_msgs) > 6:
    try:
        from session_memory import get_query_embedding
        query_embedding = await asyncio.to_thread(get_query_embedding, clean_text)
    except Exception:
        pass

system_prompt = build_system_prompt(clean_text, pattern=route_result.pattern,
                                    recitation_context=recitation_context,
                                    session_recall=session_recall)
context_length = max(2048, min(context_length, 131072))
history_msgs = window_messages(history_msgs, system_prompt, context_length,
                               query_embedding=query_embedding,
                               conversation_id=conversation_id)
messages = build_messages(history_msgs, system_prompt)
```

---

## Part B: Anti-Confabulation Constitution Update

### Problem

The model currently has no explicit guidance on how to handle retrieved content vs training knowledge. When recitation content or session recall is injected, the model should present retrieved content faithfully and flag uncertainty rather than filling gaps from training memory.

### Solution

Add an `<epistemic-honesty>` XML section to `config/constitution.txt`. This section must be carefully scoped — it should NOT make the model hedge on everything (this is an abliterated model chosen for directness). It only activates epistemic caution in three specific scenarios.

**Modify `config/constitution.txt`** — add this new section after `</precision-awareness>` (after line 93):

```xml
<epistemic-honesty>
When your response involves content from different sources, distinguish them:

Retrieved content (web search results, recitation text between BEGIN/END AUTHORITATIVE TEXT delimiters):
- Present exactly as retrieved. Do not paraphrase, reorder, or fill gaps from training memory.
- If the retrieved text appears incomplete or truncated, say so rather than completing it.
- Cite the source when available.

Session recall (earlier conversation messages between <session-recall> tags):
- Base your answer on the recalled messages. They are the authoritative record of what was discussed.
- If the user asks about something not found in recalled context or recent history, say you don't have that part of the conversation available rather than reconstructing it from inference.

Training knowledge (everything else):
- Present established facts confidently.
- For specific claims (exact dates, exact quotes, exact statistics), note if you are uncertain rather than stating with false precision.
- You are not required to hedge on general knowledge. Only flag genuine uncertainty.
</epistemic-honesty>
```

---

## Part C: Character Analysis Injection

### Problem

LLM tokenizers are blind to individual characters — they see subword tokens, not letters. When asked "How many R's are in strawberry?", the model must reason about characters it cannot directly observe. Most models (including 9B) get this wrong because the token "strawberry" doesn't decompose into visible individual letters during inference.

### Solution

Detect character-level questions (spelling, letter counting, character positions), pre-compute a character breakdown, and inject it into the system prompt so the model has the actual data to work with.

### Implementation

**Create `services/orchestrator/charmap.py`** (~60 lines):

```python
"""Character analysis injection for spelling and counting tasks."""

import re
from collections import Counter

# Patterns that indicate a character-level question
_CHARMAP_PATTERNS = [
    # "how many X in Y" / "how many X's in Y" / "how many X are in Y"
    re.compile(r"how\s+many\s+(?P<letter>[a-zA-Z])(?:'?s)?\s+(?:are\s+)?in\s+[\"']?(?P<word>\w+)[\"']?", re.I),
    # "count the letters in X" / "count the characters in X"
    re.compile(r"count\s+(?:the\s+)?(?:letters?|characters?)\s+in\s+[\"']?(?P<word>\w+)[\"']?", re.I),
    # "spell X" / "spell out X"
    re.compile(r"spell\s+(?:out\s+)?[\"']?(?P<word>\w+)[\"']?", re.I),
    # "what letters are in X"
    re.compile(r"what\s+letters?\s+(?:are\s+)?in\s+[\"']?(?P<word>\w+)[\"']?", re.I),
]


def is_charmap_request(message: str) -> tuple[bool, str]:
    """Detect character-level questions. Returns (True, word) or (False, "")."""
    msg = message.strip()
    for pattern in _CHARMAP_PATTERNS:
        m = pattern.search(msg)
        if m:
            word = m.group("word")
            if word and len(word) >= 2:
                return True, word
    return False, ""


def build_charmap(word: str) -> str:
    """Build a character analysis block for injection into the system prompt."""
    lower = word.lower()
    positions = " ".join(f"{ch}({i+1})" for i, ch in enumerate(lower))
    counts = Counter(lower)
    count_str = ", ".join(f"{ch}={n}" for ch, n in sorted(counts.items()))

    return f"""<character-analysis>
Character map for "{word}":
Position: {positions}
Total characters: {len(lower)}
Letter counts: {count_str}
Use this data to answer the user's question accurately.
</character-analysis>"""
```

**Modify `services/orchestrator/router.py`:**

Add `self.charmap_content: str = ""` to `RouteResult.__init__()` (after `self.recitation_subject` on line 43).

Import `is_charmap_request` and `build_charmap` from `charmap` at the top of the file.

In `route()`, after the recitation detection block (after line 69) and before keyword pre-routing (line 71), add:

```python
# ── Step 0b: Character analysis detection ──
from charmap import is_charmap_request, build_charmap
is_char, word = is_charmap_request(user_message)
if is_char:
    result.charmap_content = build_charmap(word)
    logger.info("Charmap detected: '%s' → word '%s'", user_message[:60], word)
```

**Modify `services/orchestrator/main.py`:**

Add `charmap_content: str = ""` parameter to `build_system_prompt()` (line 273-276). Inject it after session_recall and before vision:

```python
def build_system_prompt(user_message: str = "", has_vision: bool = False,
                        pattern: dict | None = None,
                        recitation_context: str = "",
                        session_recall: str = "",
                        charmap_content: str = "") -> str:
```

Add the injection after the session_recall block:
```python
    if charmap_content:
        parts.append(f"\n\n{charmap_content}")
```

The full layer order becomes: constitution → pattern → recitation → session_recall → charmap → vision → memories

**Pass charmap_content in ws_chat() and rest_chat():**

In ws_chat (~line 642-648), add `charmap_content=route_result.charmap_content` to the build_system_prompt call:
```python
system_prompt = build_system_prompt(
    clean_text,
    has_vision=has_vision,
    pattern=route_result.pattern,
    recitation_context=recitation_context,
    session_recall=session_recall,
    charmap_content=route_result.charmap_content,
)
```

In rest_chat (~line 838-840), same change:
```python
system_prompt = build_system_prompt(clean_text, pattern=route_result.pattern,
                                    recitation_context=recitation_context,
                                    session_recall=session_recall,
                                    charmap_content=route_result.charmap_content)
```

---

## What NOT to Do

- Do NOT modify the recitation pipeline (Phase 1) or session recall (Phase 2). This phase adds to them, not replaces them.
- Do NOT modify the UI. This is backend-only.
- Do NOT add new dependencies. Phase 3 uses only what's already installed (fastembed, numpy from Phase 2).
- Do NOT add new REST endpoints.
- Do NOT make the anti-confabulation rules overly broad. The model is abliterated for a reason — the user chose it for directness. Only apply epistemic caution to the three specific scenarios (retrieved content, session recall, uncertain specifics).
- Do NOT remove the existing FIFO fallback in window_messages. It must still work when embeddings are unavailable.

## Build, Test, and Iterate

**You are responsible for testing.** After writing the code, you MUST rebuild, deploy, and run every test below yourself. Do not just list the commands — execute them. Check each response for correctness. If a test fails, debug using `podman logs gizmo-orchestrator --tail 50`, fix the code, rebuild, and retest. Do not move on to documentation until all tests pass.

### Step 1: Rebuild and deploy

```bash
podman compose build gizmo-orchestrator && podman compose up -d gizmo-orchestrator
```

Wait for health check:
```bash
sleep 15 && curl -s http://localhost:9100/health
```

### Step 2: Test character analysis (Part C — fastest to verify)

**Test 1 — Letter counting:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=How many r's are in strawberry?" | python3 -m json.tool
```
**Expected:** Response says 3. Check logs for "Charmap detected" to confirm injection fired.

**Test 2 — Different word:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=How many s's are in mississippi?" | python3 -m json.tool
```
**Expected:** Response says 4.

**Test 3 — Spelling:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=Spell out onomatopoeia" | python3 -m json.tool
```
**Expected:** Correct spelling with all letters.

**Test 4 — False positive check:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=How many people live in Tokyo?" | python3 -m json.tool
```
**Expected:** Normal response about Tokyo's population. Should NOT trigger charmap. Check logs to confirm no "Charmap detected" line.

### Step 3: Test anti-confabulation (Part B)

**Test 5 — Recitation + epistemic honesty:**
```bash
CONV_ID="epist-test-$(date +%s)"
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=Recite the Jabberwocky by Lewis Carroll" \
  -F "conversation_id=$CONV_ID" | python3 -m json.tool
```
Then ask about a non-existent stanza:
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=Can you recite the 8th stanza of the poem?" \
  -F "conversation_id=$CONV_ID" | python3 -m json.tool
```
**Expected:** Model should say there is no 8th stanza rather than inventing one.

**Test 6 — Uncertain specific claim:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What was the exact population of ancient Rome in 117 AD?" | python3 -m json.tool
```
**Expected:** Should give an estimate but note uncertainty about the exact figure, not state a precise number with false confidence.

### Step 4: Test smart windowing (Part A)

This requires a longer conversation to push messages out of the window.

**Seed a conversation with a distinctive early topic:**
```bash
CONV_ID="window-test-$(date +%s)"

# Message 1: distinctive topic
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=I'm building a Rust compiler plugin that performs lifetime elision analysis using Petri nets. The key insight is modeling borrow regions as token flows." \
  -F "conversation_id=$CONV_ID" > /dev/null

# Messages 2-9: different topics to push message 1 out of the sliding window
for i in $(seq 2 9); do
  curl -s -X POST http://localhost:9100/api/chat \
    -F "message=Tell me an interesting fact about the number $i in nature or science." \
    -F "conversation_id=$CONV_ID" > /dev/null
  echo "Sent message $i"
  sleep 2
done
```

**Test 7 — Recall early topic:**
```bash
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What was the compiler project I mentioned? What approach was I using?" \
  -F "conversation_id=$CONV_ID" | python3 -m json.tool
```
**Expected:** Response should mention Rust, lifetime elision, Petri nets, and borrow regions. These details were in message 1, which should have been pushed out of the default FIFO window but kept by smart windowing (or retrieved by session recall). Check logs for "Session recall" to see if retrieval fired.

**Test 8 — Budget integrity:** Verify window_messages never exceeds context budget.
```bash
# Send a normal short message — should work fine with no errors
curl -s -X POST http://localhost:9100/api/chat \
  -F "message=What is 2+2?" \
  -F "context_length=4096" | python3 -m json.tool
```
**Expected:** Normal response. No context length errors.

### Step 5: If any test fails

1. Check logs: `podman logs gizmo-orchestrator --tail 50`
2. For smart windowing issues, verify embeddings exist:
   ```bash
   podman exec gizmo-orchestrator python3 -c "
   import sqlite3
   conn = sqlite3.connect('/app/memory/conversations.db')
   for r in conn.execute('SELECT conversation_id, COUNT(*) FROM session_embeddings GROUP BY conversation_id').fetchall():
       print(f'{r[0]}: {r[1]} embeddings')
   "
   ```
3. Fix the issue, rebuild: `podman compose build gizmo-orchestrator && podman compose up -d gizmo-orchestrator`
4. Rerun the failing test
5. Repeat until all 8 tests pass

**Do not proceed to documentation until all tests pass.**

## Documentation Updates (MANDATORY per CLAUDE.md)

After all tests pass, update these files:

1. **CLAUDE.md** — Add "Smart History Windowing" subsection (semantic scoring, last-6 always kept, stored embeddings, FIFO fallback). Add "Epistemic Honesty" note to constitution section. Add "Character Analysis" subsection. Update Session Log with version entry (V5.9 or whatever follows V5.8).
2. **README.md** — Update AI Capabilities: add "Smart context windowing" (keeps relevant older messages, not just most recent), add "Character analysis" (accurate letter counting and spelling). Update the memory system description to mention semantic windowing.
3. **wiki/architecture.md** — Add `charmap.py` to file tree. Update the prompt assembly diagram to show the full layer order: constitution → pattern → recitation → session_recall → charmap → vision → memories. Update the context windowing section to describe the hybrid FIFO/semantic algorithm.
4. **wiki/usage.md** — Add sections for character analysis (automatic, no user action needed) and smart windowing (automatic for conversations with 6+ messages and stored embeddings).
5. **wiki/development.md** — Update Future Roadmap: "ChromaDB semantic memory" can be marked as largely addressed by the session RAG + smart windowing combination.
6. **AUDIT.md** — Add version entry header for this change.
7. **config/constitution.txt** — Already modified as part of Part B, but verify the new section renders correctly (lines starting with # should not appear in the final constitution).
