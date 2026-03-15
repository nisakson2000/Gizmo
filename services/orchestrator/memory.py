"""File-based memory system with BM25 retrieval and path traversal protection."""

import os
import re
import time
from pathlib import Path
from typing import Optional

from rank_bm25 import BM25Okapi

MEMORY_ROOT = Path("/app/memory")
SUBDIRS = ["facts", "conversations", "notes"]
MAX_INJECT = 5
MAX_INJECT_CHARS = 800

STOP_WORDS = frozenset({
    "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
    "have", "has", "had", "do", "does", "did", "will", "would", "could",
    "should", "may", "might", "shall", "can", "need", "dare", "ought",
    "i", "me", "my", "mine", "we", "us", "our", "ours", "you", "your",
    "yours", "he", "him", "his", "she", "her", "hers", "it", "its",
    "they", "them", "their", "theirs", "what", "which", "who", "whom",
    "this", "that", "these", "those", "am", "in", "on", "at", "to",
    "for", "of", "with", "by", "from", "as", "into", "through", "during",
    "before", "after", "above", "below", "between", "and", "but", "or",
    "nor", "not", "so", "very", "just", "about", "up", "out", "if", "then",
})


def _tokenize(text: str) -> list[str]:
    """Lowercase, split on non-alpha, filter stop words and short tokens."""
    tokens = re.findall(r"[a-zA-Z]+", text.lower())
    return [t for t in tokens if t not in STOP_WORDS and len(t) > 1]


def _safe_path(filename: str, subdir: str = "facts") -> Optional[Path]:
    """Resolve filename to a safe path within the memory directory."""
    if subdir not in SUBDIRS:
        return None
    safe_name = re.sub(r"[^a-zA-Z0-9_\-.]", "", filename)
    if not safe_name or safe_name.startswith("."):
        return None
    target = (MEMORY_ROOT / subdir / safe_name).resolve()
    if not target.is_relative_to(MEMORY_ROOT.resolve()):
        return None
    return target


def write_memory(filename: str, content: str, subdir: str = "facts") -> str:
    """Write content to a memory file."""
    path = _safe_path(filename, subdir)
    if path is None:
        return "Error: invalid filename or path"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return f"Saved to {subdir}/{filename}"


def read_memory(filename: str, subdir: str = "facts") -> str:
    """Read content from a memory file."""
    path = _safe_path(filename, subdir)
    if path is None:
        return "Error: invalid filename or path"
    if not path.exists():
        return f"Memory file '{filename}' not found in {subdir}/"
    return path.read_text(encoding="utf-8")


def list_memories(subdir: Optional[str] = None) -> list[dict]:
    """List all memory files, optionally filtered by subdirectory."""
    results = []
    dirs = [subdir] if subdir and subdir in SUBDIRS else SUBDIRS
    for d in dirs:
        dir_path = MEMORY_ROOT / d
        if not dir_path.exists():
            continue
        for f in sorted(dir_path.iterdir()):
            if f.is_file() and not f.name.startswith("."):
                stat = f.stat()
                results.append({
                    "filename": f.name,
                    "subdir": d,
                    "size": stat.st_size,
                    "modified": stat.st_mtime,
                })
    return results


def delete_memory(filename: str, subdir: str = "facts") -> str:
    """Delete a memory file."""
    path = _safe_path(filename, subdir)
    if path is None:
        return "Error: invalid filename or path"
    if not path.exists():
        return f"Memory file '{filename}' not found in {subdir}/"
    path.unlink()
    return f"Deleted {subdir}/{filename}"


def get_relevant_memories(query: str) -> list[str]:
    """BM25-ranked memory retrieval for system prompt injection.

    Scores documents using BM25Okapi with a mild recency boost.
    """
    query_tokens = _tokenize(query)
    if not query_tokens:
        return []

    documents = []  # (subdir, filename, content, mtime)
    corpus = []

    for subdir in SUBDIRS:
        dir_path = MEMORY_ROOT / subdir
        if not dir_path.exists():
            continue
        for f in dir_path.iterdir():
            if not f.is_file() or f.name.startswith("."):
                continue
            try:
                content = f.read_text(encoding="utf-8")
            except Exception:
                continue
            name_text = f.name.replace(".txt", "").replace("_", " ")
            doc_text = f"{name_text} {content}"
            doc_tokens = _tokenize(doc_text)
            if not doc_tokens:
                continue
            corpus.append(doc_tokens)
            documents.append((subdir, f.name, content, f.stat().st_mtime))

    if not corpus:
        return []

    bm25 = BM25Okapi(corpus)
    scores = bm25.get_scores(query_tokens)

    now = time.time()
    scored = []
    for i, score in enumerate(scores):
        if score <= 0:
            continue
        subdir, filename, content, mtime = documents[i]
        # Mild recency boost: up to 1.5x for files modified in the last 30 days
        age_days = (now - mtime) / 86400
        recency = 1.0 + 0.5 * max(0.0, 1.0 - age_days / 30.0)
        final_score = score * recency
        snippet = content[:MAX_INJECT_CHARS]
        scored.append((final_score, f"{subdir}/{filename}: {snippet}"))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [s[1] for s in scored[:MAX_INJECT]]
