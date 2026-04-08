"""Cross-conversation semantic search with MemPalace-inspired room categorization.

Indexes exchange pairs (user+assistant) from all conversations into
cross_conv_embeddings for semantic retrieval across conversation boundaries.
Uses the same fastembed model as session recall — no new dependencies.
"""

import logging
import re
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from session_memory import embed_text, cosine_sim

logger = logging.getLogger(__name__)

DB_PATH = Path("/app/memory/conversations.db")

# MemPalace-inspired room classification via keyword scoring
ROOM_KEYWORDS: dict[str, list[str]] = {
    "technical": [
        "code", "debug", "api", "database", "bug", "error", "function",
        "deploy", "container", "server", "script", "compile", "runtime",
        "library", "framework", "sql", "python", "javascript",
    ],
    "architecture": [
        "design", "pattern", "schema", "service", "endpoint",
        "migration", "refactor", "module", "layer", "interface",
        "protocol", "infrastructure",
    ],
    "planning": [
        "roadmap", "milestone", "sprint", "deadline", "schedule",
        "priority", "task", "timeline", "goal", "phase", "plan",
    ],
    "decisions": [
        "decided", "chose", "switched", "prefer", "trade-off",
        "option", "approach", "instead", "alternative", "versus",
    ],
    "problems": [
        "issue", "broken", "fix", "workaround", "failed", "crash",
        "regression", "incident", "outage", "resolved",
    ],
}

_WORD_RE = re.compile(r"[a-zA-Z]+")


def classify_room(text: str) -> str:
    """Classify text into a topic room by keyword scoring. Tie or no hits → 'general'."""
    words = set(w.lower() for w in _WORD_RE.findall(text))
    best_room = "general"
    best_count = 0
    tied = False
    for room, keywords in ROOM_KEYWORDS.items():
        count = sum(1 for kw in keywords if kw in words)
        if count > best_count:
            best_count = count
            best_room = room
            tied = False
        elif count == best_count and count > 0:
            tied = True
    return "general" if tied or best_count == 0 else best_room


def index_cross_conversation(
    conversation_id: str,
    user_text: str,
    assistant_text: str,
    user_msg_index: int,
    asst_msg_index: int,
) -> None:
    """Index a user+assistant exchange pair for cross-conversation search.

    Safe to call from a background thread.
    """
    try:
        combined = f"{user_text}\n{assistant_text}"
        embedding = embed_text(combined[:2000])
        room = classify_room(combined)

        from importance import score_message
        importance = max(
            score_message(user_text, role="user"),
            score_message(assistant_text, role="assistant"),
        )

        conn = sqlite3.connect(str(DB_PATH))
        try:
            conn.execute(
                """INSERT INTO cross_conv_embeddings
                   (conversation_id, chunk_type, chunk_text, embedding,
                    message_start, message_end, topic_category, importance, created_at)
                   VALUES (?, 'exchange', ?, ?, ?, ?, ?, ?, datetime('now'))""",
                (conversation_id, combined[:600], embedding,
                 user_msg_index, asst_msg_index, room, importance),
            )
            conn.commit()
        finally:
            conn.close()
        logger.debug("Indexed cross-conv: conv=%s room=%s importance=%.2f",
                      conversation_id, room, importance)
    except Exception as e:
        logger.warning("index_cross_conversation failed: %s", e)


def index_summary(conversation_id: str, summary: str, topic: str) -> None:
    """Embed a conversation summary and store as chunk_type='summary' for two-tier search.

    Safe to call from a background thread.
    """
    try:
        embedding = embed_text(summary[:2000])
        conn = sqlite3.connect(str(DB_PATH))
        try:
            conn.execute(
                """INSERT INTO cross_conv_embeddings
                   (conversation_id, chunk_type, chunk_text, embedding,
                    topic_category, importance, created_at)
                   VALUES (?, 'summary', ?, ?, ?, 0.8, datetime('now'))""",
                (conversation_id, summary[:600], embedding, topic),
            )
            conn.commit()
        finally:
            conn.close()
        logger.debug("Indexed summary: conv=%s topic=%s", conversation_id, topic)
    except Exception as e:
        logger.warning("index_summary failed: %s", e)


def search_cross_conversations(
    query: str,
    current_conversation_id: str,
    top_k: int = 3,
    query_embedding: bytes | None = None,
) -> list[dict]:
    """Search all conversations for semantically relevant exchanges.

    Uses two-tier search when summary embeddings exist:
    1. Search summaries to find top-5 relevant conversations
    2. Drill into exchange-level embeddings for those conversations
    Falls back to flat exchange search if no summaries are available.

    Excludes the current conversation. Returns up to top_k results
    with similarity > 0.45, each with conversation title and date.
    """
    try:
        if query_embedding:
            query_vec = np.frombuffer(query_embedding, dtype=np.float32)
        else:
            query_vec = np.frombuffer(embed_text(query[:2000]), dtype=np.float32)

        conn = sqlite3.connect(str(DB_PATH))
        conn.row_factory = sqlite3.Row
        try:
            # Check if summary embeddings exist for two-tier search
            summary_count = conn.execute(
                "SELECT COUNT(*) FROM cross_conv_embeddings WHERE chunk_type = 'summary'"
            ).fetchone()[0]

            if summary_count > 0:
                results = _two_tier_search(conn, query_vec, current_conversation_id, top_k)
                if results:
                    return results

            # Fall back to flat exchange search (with optional room filter)
            room_filter = None
            query_room = classify_room(query)
            if query_room != "general":
                words = set(w.lower() for w in _WORD_RE.findall(query))
                hits = sum(1 for kw in ROOM_KEYWORDS[query_room] if kw in words)
                if hits >= 3:
                    room_filter = query_room

            if room_filter:
                results = _search_with_filter(conn, query_vec, current_conversation_id,
                                              top_k, room_filter)
                if results:
                    return results
            return _search_with_filter(conn, query_vec, current_conversation_id, top_k)
        finally:
            conn.close()
    except Exception as e:
        logger.warning("search_cross_conversations failed: %s", e)
        return []


def _two_tier_search(
    conn: sqlite3.Connection,
    query_vec: np.ndarray,
    exclude_conv_id: str,
    top_k: int,
) -> list[dict]:
    """Tier 1: search summaries for top-5 conversations, Tier 2: drill into exchanges."""
    # Tier 1: search summary embeddings
    summary_rows = conn.execute(
        """SELECT ce.conversation_id, ce.embedding
           FROM cross_conv_embeddings ce
           WHERE ce.chunk_type = 'summary' AND ce.conversation_id != ?""",
        (exclude_conv_id,),
    ).fetchall()

    if not summary_rows:
        return []

    conv_scores: dict[str, float] = {}
    for row in summary_rows:
        stored_vec = np.frombuffer(row["embedding"], dtype=np.float32)
        sim = cosine_sim(query_vec, stored_vec)
        conv_id = row["conversation_id"]
        # Keep best score per conversation
        if conv_id not in conv_scores or sim > conv_scores[conv_id]:
            conv_scores[conv_id] = sim

    # Top-5 conversations by summary similarity
    top_convs = sorted(conv_scores.items(), key=lambda x: x[1], reverse=True)[:5]
    top_conv_ids = [c[0] for c in top_convs if c[1] > 0.3]

    if not top_conv_ids:
        return []

    # Tier 2: search exchange embeddings within top conversations
    placeholders = ",".join("?" * len(top_conv_ids))
    exchange_rows = conn.execute(
        f"""SELECT ce.conversation_id, ce.chunk_text, ce.embedding,
                   ce.topic_category, ce.importance, ce.created_at,
                   c.title
            FROM cross_conv_embeddings ce
            JOIN conversations c ON ce.conversation_id = c.id
            WHERE ce.chunk_type = 'exchange'
              AND ce.conversation_id IN ({placeholders})""",
        top_conv_ids,
    ).fetchall()

    scored = []
    for row in exchange_rows:
        stored_vec = np.frombuffer(row["embedding"], dtype=np.float32)
        sim = cosine_sim(query_vec, stored_vec)
        if sim > 0.45:
            scored.append({
                "conversation_id": row["conversation_id"],
                "title": row["title"] or "Untitled",
                "chunk_text": row["chunk_text"],
                "topic_category": row["topic_category"],
                "importance": row["importance"],
                "created_at": row["created_at"],
                "similarity": sim,
            })

    scored.sort(key=lambda x: x["similarity"], reverse=True)
    return scored[:top_k]


def _search_with_filter(
    conn: sqlite3.Connection,
    query_vec: np.ndarray,
    exclude_conv_id: str,
    top_k: int,
    room_filter: str | None = None,
) -> list[dict]:
    """Internal: search cross_conv_embeddings with optional room filter."""
    query = """SELECT ce.conversation_id, ce.chunk_text, ce.embedding,
                      ce.topic_category, ce.importance, ce.created_at,
                      c.title
               FROM cross_conv_embeddings ce
               JOIN conversations c ON ce.conversation_id = c.id
               WHERE ce.conversation_id != ? AND ce.chunk_type = 'exchange'"""
    params: list = [exclude_conv_id]

    if room_filter:
        query += " AND ce.topic_category = ?"
        params.append(room_filter)

    rows = conn.execute(query, params).fetchall()
    if not rows:
        return []

    scored = []
    for row in rows:
        stored_vec = np.frombuffer(row["embedding"], dtype=np.float32)
        sim = cosine_sim(query_vec, stored_vec)
        if sim > 0.45:
            scored.append({
                "conversation_id": row["conversation_id"],
                "title": row["title"] or "Untitled",
                "chunk_text": row["chunk_text"],
                "topic_category": row["topic_category"],
                "importance": row["importance"],
                "created_at": row["created_at"],
                "similarity": sim,
            })

    scored.sort(key=lambda x: x["similarity"], reverse=True)
    return scored[:top_k]


def format_cross_recall(results: list[dict]) -> str:
    """Format cross-conversation search results as XML block for system prompt."""
    if not results:
        return ""

    lines = [
        "<cross-conversation-recall>",
        "Relevant context from previous conversations:",
        "",
    ]
    for r in results:
        date_str = r["created_at"][:10] if r["created_at"] else "unknown date"
        preview = r["chunk_text"][:300]
        lines.append(f'[Conv "{r["title"]}", {date_str}]: {preview}')

    lines.append("")
    lines.append("Use this recalled context when the user references previous conversations.")
    lines.append("If the user asks about something from another conversation, use these results")
    lines.append("rather than guessing or hallucinating details.")
    lines.append("</cross-conversation-recall>")

    return "\n".join(lines)


def backfill_cross_conv_embeddings() -> None:
    """One-time startup migration: index existing conversations into cross_conv_embeddings.

    Groups session_embeddings into exchange pairs (user+assistant by consecutive
    message_index), classifies rooms, and stores in cross_conv_embeddings.
    Skips conversations that already have entries (idempotent).
    """
    try:
        conn = sqlite3.connect(str(DB_PATH))
        conn.row_factory = sqlite3.Row
        try:
            # Find conversations with session_embeddings but no cross_conv entries
            conv_ids = conn.execute("""
                SELECT DISTINCT se.conversation_id
                FROM session_embeddings se
                WHERE se.conversation_id NOT IN (
                    SELECT DISTINCT conversation_id FROM cross_conv_embeddings
                )
            """).fetchall()

            if not conv_ids:
                logger.info("Cross-conv backfill: nothing to backfill")
                return

            total_indexed = 0
            for row in conv_ids:
                conv_id = row["conversation_id"]
                # Get all turns ordered by message_index
                turns = conn.execute(
                    """SELECT message_index, role, content
                       FROM session_embeddings
                       WHERE conversation_id = ?
                       ORDER BY message_index""",
                    (conv_id,),
                ).fetchall()

                # Group into exchange pairs (user followed by assistant)
                i = 0
                while i < len(turns) - 1:
                    if turns[i]["role"] == "user" and turns[i + 1]["role"] == "assistant":
                        user_text = turns[i]["content"]
                        asst_text = turns[i + 1]["content"]
                        user_idx = turns[i]["message_index"]
                        asst_idx = turns[i + 1]["message_index"]

                        combined = f"{user_text}\n{asst_text}"
                        embedding = embed_text(combined[:2000])
                        room = classify_room(combined)

                        from importance import score_message
                        importance = max(
                            score_message(user_text, role="user"),
                            score_message(asst_text, role="assistant"),
                        )

                        conn.execute(
                            """INSERT INTO cross_conv_embeddings
                               (conversation_id, chunk_type, chunk_text, embedding,
                                message_start, message_end, topic_category, importance, created_at)
                               VALUES (?, 'exchange', ?, ?, ?, ?, ?, ?, datetime('now'))""",
                            (conv_id, combined[:600], embedding,
                             user_idx, asst_idx, room, importance),
                        )
                        total_indexed += 1
                        i += 2
                    else:
                        i += 1

            conn.commit()
            logger.info("Cross-conv backfill: indexed %d exchange pairs from %d conversations",
                        total_indexed, len(conv_ids))
        finally:
            conn.close()
    except Exception as e:
        logger.warning("Cross-conv backfill failed: %s", e)
