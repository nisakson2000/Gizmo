"""Tool definitions and dispatch for LLM function calling."""

from typing import Any

from memory import write_memory, read_memory, list_memories
from sandbox import run_code
from search import web_search, format_search_results

# OpenAI function-calling format tool definitions
TOOL_DEFINITIONS = [
    {
        "type": "function",
        "function": {
            "name": "web_search",
            "description": "Search the web for current information. Use when the user asks about recent events, news, or anything that may not be in your training data.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "The search query",
                    }
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "read_memory",
            "description": "Read a previously saved memory file. Use when the user asks about something you may have remembered.",
            "parameters": {
                "type": "object",
                "properties": {
                    "filename": {
                        "type": "string",
                        "description": "The memory filename to read (e.g. 'user_name.txt')",
                    },
                    "subdir": {
                        "type": "string",
                        "enum": ["facts", "conversations", "notes"],
                        "description": "Memory subdirectory (default: facts)",
                    },
                },
                "required": ["filename"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "write_memory",
            "description": "Save information to persistent memory. ONLY use when the user explicitly asks you to remember or save something. Do not proactively save observations about the conversation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "filename": {
                        "type": "string",
                        "description": "Filename for the memory (e.g. 'user_name.txt')",
                    },
                    "content": {
                        "type": "string",
                        "description": "The content to save",
                    },
                    "subdir": {
                        "type": "string",
                        "enum": ["facts", "conversations", "notes"],
                        "description": "Memory subdirectory (default: facts)",
                    },
                },
                "required": ["filename", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_memories",
            "description": "List all saved memory files. Use when the user asks what you remember.",
            "parameters": {
                "type": "object",
                "properties": {
                    "subdir": {
                        "type": "string",
                        "enum": ["facts", "conversations", "notes"],
                        "description": "Filter to specific subdirectory (optional)",
                    },
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "run_code",
            "description": "Execute Python code in a sandboxed container. Use ONLY when the user asks you to run code, do a calculation, or when a task clearly requires computation. Do not use for conversational or creative tasks. Available libraries: numpy, pandas, matplotlib, sympy, scipy. No network access.",
            "parameters": {
                "type": "object",
                "properties": {
                    "code": {
                        "type": "string",
                        "description": "Python code to execute",
                    },
                    "timeout": {
                        "type": "integer",
                        "description": "Execution timeout in seconds (default 10, max 30)",
                        "minimum": 1,
                        "maximum": 30,
                    },
                },
                "required": ["code"],
            },
        },
    },
]


async def execute_tool(name: str, arguments: dict[str, Any]) -> str:
    """Execute a tool by name and return the result as a string."""
    if name == "web_search":
        results = await web_search(arguments["query"])
        return format_search_results(results)

    elif name == "read_memory":
        return read_memory(
            arguments["filename"],
            arguments.get("subdir", "facts"),
        )

    elif name == "write_memory":
        return write_memory(
            arguments["filename"],
            arguments["content"],
            arguments.get("subdir", "facts"),
        )

    elif name == "list_memories":
        memories = list_memories(arguments.get("subdir"))
        if not memories:
            return "No memories saved yet."
        lines = ["Saved memories:\n"]
        for m in memories:
            lines.append(f"- {m['subdir']}/{m['filename']} ({m['size']} bytes)")
        return "\n".join(lines)

    elif name == "run_code":
        result = await run_code(
            arguments["code"],
            arguments.get("timeout", 10),
        )
        parts = []
        if result["timed_out"]:
            parts.append(f"[TIMED OUT after {arguments.get('timeout', 10)}s]")
        if result["stdout"]:
            parts.append(f"stdout:\n{result['stdout']}")
        if result["stderr"]:
            parts.append(f"stderr:\n{result['stderr']}")
        if not parts:
            parts.append(f"(no output, exit code {result['exit_code']})")
        return "\n".join(parts)

    else:
        return f"Unknown tool: {name}"
