"""Conversation compaction — rolling LLM summaries of older conversation segments.

Generates concise summaries of conversation segments that have scrolled out of
the sliding window, preventing context rot in long conversations. Summaries are
injected into the system prompt and indexed for cross-conversation search.

All LLM calls are local (orchestrator → gizmo-llama within Podman network).
"""

import asyncio
import logging
import os
import sqlite3
from pathlib import Path

import httpx

logger = logging.getLogger("gizmo.conversations")

DB_PATH = Path("/app/memory/conversations.db")
LLAMA_HOST = os.getenv("LLAMA_HOST", "gizmo-llama")
LLAMA_PORT = os.getenv("LLAMA_PORT", "8080")
LLAMA_URL = f"http://{LLAMA_HOST}:{LLAMA_PORT}"

COMPACTION_MIN_MESSAGES = int(os.getenv("COMPACTION_MIN_MESSAGES", "20"))
SEGMENT_SIZE = 10  # messages per segment (5 exchange pairs)
SKIP_RECENT = 12   # don't summarize the most recent N messages

SUMMARIZATION_PROMPT = """Summarize this conversation segment concisely. Focus on:
- Key topics discussed and decisions made
- Important facts shared by the user
- User preferences or corrections expressed
- Tool calls and their key results (not raw JSON)
- Any unresolved questions

Output 2-4 sentences. Be factual and specific. Preserve names, numbers, and technical terms.
Do not add commentary or meta-observations."""


async def maybe_compact(conversation_id: str, message_count: int) -> None:
    """Check if compaction is needed and run it for the oldest uncovered segment.

    Fire-and-forget — called via asyncio.create_task after response save.
    Only compacts one segment per call to keep background work minimal.
    """
    if message_count < COMPACTION_MIN_MESSAGES:
        return

    try:
        conn = sqlite3.connect(str(DB_PATH))
        conn.row_factory = sqlite3.Row
        try:
            # Find the highest segment_end already summarized
            row = conn.execute(
                "SELECT MAX(segment_end) as max_end FROM conversation_summaries WHERE conversation_id = ?",
                (conversation_id,),
            ).fetchone()
            summarized_up_to = row["max_end"] if row and row["max_end"] is not None else -1

            # Calculate the compactable range (everything except the most recent SKIP_RECENT)
            compactable_end = message_count - SKIP_RECENT
            if compactable_end <= summarized_up_to + 1:
                return  # nothing new to compact

            # Determine the next segment to compact
            segment_start = summarized_up_to + 1
            segment_end = min(segment_start + SEGMENT_SIZE - 1, compactable_end - 1)

            if segment_end - segment_start < 3:
                return  # too few messages for a meaningful summary

            # Load segment messages from DB
            messages = conn.execute(
                """SELECT role, content FROM messages
                   WHERE conversation_id = ?
                   ORDER BY id ASC
                   LIMIT ? OFFSET ?""",
                (conversation_id, segment_end - segment_start + 1, segment_start),
            ).fetchall()
        finally:
            conn.close()

        if not messages:
            return

        # Build segment text for summarization
        segment_lines = []
        for msg in messages:
            role = msg["role"].title()
            content = msg["content"] or ""
            # Truncate very long messages
            if len(content) > 500:
                content = content[:500] + "..."
            segment_lines.append(f"{role}: {content}")
        segment_text = "\n".join(segment_lines)

        # Call local LLM for summary
        summary = await _summarize_segment(segment_text)
        if not summary:
            return

        # Classify topic from the segment text
        from cross_memory import classify_room
        topic = classify_room(segment_text)

        # Store summary
        token_count = len(summary) // 4  # rough estimate
        conn = sqlite3.connect(str(DB_PATH))
        try:
            conn.execute(
                """INSERT INTO conversation_summaries
                   (conversation_id, segment_start, segment_end, summary, topic, token_count, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, datetime('now'))""",
                (conversation_id, segment_start, segment_end, summary, topic, token_count),
            )
            conn.commit()
        finally:
            conn.close()

        # Index summary embedding for cross-conv two-tier search
        from cross_memory import index_summary  # deferred: compaction → cross_memory dependency
        await asyncio.to_thread(index_summary, conversation_id, summary, topic)

        logger.info("Compaction: conv=%s segment=[%d-%d] topic=%s tokens=%d",
                     conversation_id, segment_start, segment_end, topic, token_count)

    except Exception as e:
        logger.warning("Compaction failed for %s: %s", conversation_id, e)


async def _summarize_segment(segment_text: str) -> str:
    """Call the local LLM to generate a concise summary of a conversation segment."""
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                f"{LLAMA_URL}/v1/chat/completions",
                json={
                    "model": "gizmo",
                    "messages": [
                        {"role": "system", "content": SUMMARIZATION_PROMPT},
                        {"role": "user", "content": segment_text},
                    ],
                    "stream": False,
                    "max_tokens": 200,
                    "temperature": 0.3,
                    "chat_template_kwargs": {"enable_thinking": False},
                },
            )
            if resp.status_code != 200:
                logger.warning("Summarization LLM returned %d", resp.status_code)
                return ""
            data = resp.json()
            return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        logger.warning("Summarization LLM call failed: %s", e)
        return ""


def get_conversation_summary(conversation_id: str, max_tokens: int = 400) -> str:
    """Load and format all summaries for a conversation as a system prompt block.

    Returns empty string if no summaries exist. Keeps only the most recent
    segments if total exceeds max_tokens budget.
    """
    try:
        conn = sqlite3.connect(str(DB_PATH))
        conn.row_factory = sqlite3.Row
        try:
            rows = conn.execute(
                """SELECT segment_start, segment_end, summary, token_count
                   FROM conversation_summaries
                   WHERE conversation_id = ?
                   ORDER BY segment_start ASC""",
                (conversation_id,),
            ).fetchall()
        finally:
            conn.close()

        if not rows:
            return ""

        # Trim from oldest if over budget
        segments = list(rows)
        total_tokens = sum(r["token_count"] for r in segments)
        while total_tokens > max_tokens and len(segments) > 1:
            total_tokens -= segments[0]["token_count"]
            segments.pop(0)

        lines = [
            "<conversation-summary>",
            "Summary of earlier discussion in this conversation:",
            "",
        ]
        for seg in segments:
            lines.append(f"[Messages {seg['segment_start']}-{seg['segment_end']}]: {seg['summary']}")

        lines.append("</conversation-summary>")
        return "\n".join(lines)

    except Exception as e:
        logger.warning("get_conversation_summary failed: %s", e)
        return ""
