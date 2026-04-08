"""Heuristic-based message importance scoring for V6 memory system.

Scores messages on a 0.0-1.0 scale using string heuristics only — no LLM calls.
Used by cross-conversation memory to prioritize what gets indexed and recalled.
"""

import re

_CORRECTION_RE = re.compile(
    r"\b(?:actually|no,|wrong|I meant|correct that|not what I meant|let me clarify)\b", re.I
)
_CODE_BLOCK_RE = re.compile(r"```")
_URL_RE = re.compile(r"https?://|/[\w.-]+/[\w.-]+")
_QUESTION_RE = re.compile(r"\?")


def score_message(content: str, role: str = "user", has_tool_calls: bool = False) -> float:
    """Score a message's importance using additive heuristics, capped at 1.0.

    Args:
        content: The message text.
        role: "user" or "assistant".
        has_tool_calls: Whether the message triggered or contains tool call results.

    Returns:
        Float between 0.0 and 1.0.
    """
    score = 0.3  # base

    if has_tool_calls:
        score += 0.2

    if role == "user" and _QUESTION_RE.search(content):
        score += 0.1

    if _CODE_BLOCK_RE.search(content):
        score += 0.1

    if _CORRECTION_RE.search(content):
        score += 0.2

    if len(content.strip()) < 20:
        score -= 0.1

    if _URL_RE.search(content):
        score += 0.1

    return max(0.0, min(1.0, score))
