"""Usage analytics for Gizmo — stores per-message metrics and computes cost comparisons."""

import logging
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

logger = logging.getLogger("gizmo.error")

DB_PATH = Path("/app/memory/conversations.db")

# Approximate pricing per 1M tokens (mid-2026, update as needed)
COST_MODELS = {
    "OpenAI GPT-4o": {"input": 2.50, "output": 10.00},
    "OpenAI GPT-4o-mini": {"input": 0.15, "output": 0.60},
    "Claude Sonnet 4": {"input": 3.00, "output": 15.00},
    "Claude Opus 4": {"input": 15.00, "output": 75.00},
    "Gemini 2.5 Pro": {"input": 1.25, "output": 10.00},
    "Gemini 2.5 Flash": {"input": 0.15, "output": 0.60},
}


def _get_db():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def store_analytics(
    conversation_id: str,
    message_index: int,
    prompt_tokens: int | None = None,
    completion_tokens: int | None = None,
    total_tokens: int | None = None,
    response_time_ms: int | None = None,
    context_build_ms: int | None = None,
    tool_rounds: int = 0,
    mode: str = "chat",
):
    """Store per-message analytics after an assistant response."""
    conn = _get_db()
    try:
        conn.execute(
            """INSERT INTO message_analytics
               (conversation_id, message_index, prompt_tokens, completion_tokens, total_tokens,
                response_time_ms, context_build_ms, tool_rounds, mode, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (conversation_id, message_index, prompt_tokens, completion_tokens, total_tokens,
             response_time_ms, context_build_ms, tool_rounds, mode,
             datetime.now(timezone.utc).isoformat()),
        )
        conn.commit()
    except Exception as e:
        logger.error("Failed to store analytics: %s", e)
    finally:
        conn.close()


def get_summary() -> dict:
    """Total tokens, total conversations, average response time, total estimated savings."""
    conn = _get_db()
    try:
        row = conn.execute("""
            SELECT
                COALESCE(SUM(prompt_tokens), 0) AS total_prompt,
                COALESCE(SUM(completion_tokens), 0) AS total_completion,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS total_messages,
                COUNT(DISTINCT conversation_id) AS total_conversations,
                COALESCE(AVG(response_time_ms), 0) AS avg_response_ms,
                COALESCE(AVG(context_build_ms), 0) AS avg_context_ms
            FROM message_analytics
        """).fetchone()

        total_prompt = row["total_prompt"]
        total_completion = row["total_completion"]

        # Compute per-provider costs from the totals we already have
        providers = _compute_provider_costs(total_prompt, total_completion)

        return {
            "total_prompt_tokens": total_prompt,
            "total_completion_tokens": total_completion,
            "total_tokens": row["total_tokens"],
            "total_messages": row["total_messages"],
            "total_conversations": row["total_conversations"],
            "avg_response_ms": round(row["avg_response_ms"]),
            "avg_context_ms": round(row["avg_context_ms"]),
            "estimated_savings_usd": providers[0]["estimated_cost_usd"] if providers else 0.0,
            "providers": providers,
        }
    finally:
        conn.close()


def get_daily_breakdown(days: int = 30) -> list[dict]:
    """Daily breakdown of tokens, messages, and response times."""
    conn = _get_db()
    try:
        rows = conn.execute("""
            SELECT
                DATE(created_at) AS date,
                COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS messages,
                COALESCE(AVG(response_time_ms), 0) AS avg_response_ms
            FROM message_analytics
            WHERE created_at >= DATE('now', ?)
            GROUP BY DATE(created_at)
            ORDER BY date ASC
        """, (f"-{days} days",)).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def get_conversation_usage() -> list[dict]:
    """Per-conversation token totals, sorted by usage (top 20)."""
    conn = _get_db()
    try:
        rows = conn.execute("""
            SELECT
                ma.conversation_id,
                c.title,
                COALESCE(SUM(ma.prompt_tokens), 0) AS prompt_tokens,
                COALESCE(SUM(ma.completion_tokens), 0) AS completion_tokens,
                COALESCE(SUM(ma.total_tokens), 0) AS total_tokens,
                COUNT(*) AS messages,
                MAX(ma.created_at) AS last_active
            FROM message_analytics ma
            LEFT JOIN conversations c ON c.id = ma.conversation_id
            GROUP BY ma.conversation_id
            ORDER BY total_tokens DESC
            LIMIT 20
        """).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def _compute_provider_costs(total_prompt: int, total_completion: int) -> list[dict]:
    """Compute estimated cost for each cloud provider. Sorted most expensive first."""
    providers = []
    for name, pricing in COST_MODELS.items():
        cost = (total_prompt / 1_000_000 * pricing["input"] +
                total_completion / 1_000_000 * pricing["output"])
        providers.append({
            "provider": name,
            "input_price_per_1m": pricing["input"],
            "output_price_per_1m": pricing["output"],
            "estimated_cost_usd": round(cost, 2),
        })
    providers.sort(key=lambda p: p["estimated_cost_usd"], reverse=True)
    return providers


def get_cost_comparison() -> dict:
    """Cost comparison across all providers (standalone endpoint)."""
    conn = _get_db()
    try:
        row = conn.execute("""
            SELECT
                COALESCE(SUM(prompt_tokens), 0) AS total_prompt,
                COALESCE(SUM(completion_tokens), 0) AS total_completion
            FROM message_analytics
        """).fetchone()
        total_prompt = row["total_prompt"]
        total_completion = row["total_completion"]
        return {
            "total_prompt_tokens": total_prompt,
            "total_completion_tokens": total_completion,
            "providers": _compute_provider_costs(total_prompt, total_completion),
        }
    finally:
        conn.close()


def get_mode_breakdown() -> list[dict]:
    """Token distribution across modes."""
    conn = _get_db()
    try:
        rows = conn.execute("""
            SELECT
                COALESCE(mode, 'chat') AS mode,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS messages
            FROM message_analytics
            GROUP BY mode
            ORDER BY total_tokens DESC
        """).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()
