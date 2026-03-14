# Architecture

Full technical reference for Gizmo-AI. Assumes familiarity with containers and REST APIs.

---

## System Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                         HOST MACHINE                              │
│                    (Bazzite OS, RTX 4090)                         │
│                                                                    │
│  ┌─────────────────────── gizmo-net ───────────────────────┐      │
│  │                      10.90.0.0/24                        │      │
│  │                                                          │      │
│  │  ┌──────────┐    ┌──────────────┐    ┌───────────────┐  │      │
│  │  │ gizmo-ui │───▶│ gizmo-       │───▶│ gizmo-llama   │  │      │
│  │  │ :3100    │    │ orchestrator │    │ :8080         │  │      │
│  │  │ SvelteKit│    │ :9100 FastAPI│    │ llama.cpp     │  │      │
│  │  │ nginx    │    └──────┬───────┘    │ Q8_0 9B      │  │      │
│  │  └──────────┘           │            │ + mmproj      │  │      │
│  │              ┌──────────┼────────┐   │ [GPU]         │  │      │
│  │              │          │        │   └───────────────┘  │      │
│  │         ┌────▼─────┐ ┌─▼──────┐ ┌▼────────────┐        │      │
│  │         │gizmo-    │ │gizmo-  │ │gizmo-       │        │      │
│  │         │searxng   │ │tts     │ │whisper      │        │      │
│  │         │:8300     │ │:8400   │ │:8200        │        │      │
│  │         │(8080)    │ │[GPU]   │ │[CPU]        │        │      │
│  │         └──────────┘ └────────┘ └─────────────┘        │      │
│  └────────────────────────────────────────────────────────┘      │
│                                                                    │
│  Volumes: ~/gizmo-ai/models, ~/gizmo-ai/memory, ~/gizmo-ai/logs  │
│           ~/gizmo-ai/voices, ~/gizmo-ai/media                     │
└──────────────────────────────────────────────────────────────────┘
         ▲                                    ▲
  Browser/App                           Tailscale HTTPS
  localhost:3100                 bazzite.tail163501.ts.net
```

## Container Reference

| Container | Image | Role | Container Port | Host Port | GPU | Depends On |
|-----------|-------|------|---------------|-----------|-----|------------|
| gizmo-llama | gizmo-llama:latest (built from source) | LLM + vision inference | 8080 | 8080 | Yes (RTX 4090) | — |
| gizmo-orchestrator | gizmo-orchestrator:latest (built) | API gateway, routing, tools | 9100 | 9100 | No | gizmo-llama |
| gizmo-ui | gizmo-ui:latest (built) | Web UI (SvelteKit + nginx) | 3100 | 3100 | No | gizmo-orchestrator |
| gizmo-searxng | searxng/searxng:latest | Web search engine | 8080 | 8300 | No | — |
| gizmo-tts | gizmo-tts:latest (built) | Text-to-speech (Qwen3-TTS) | 8400 | 8400 | Yes (RTX 4090) | — |
| gizmo-whisper | fedirz/faster-whisper-server:0.5.0-cpu | Speech-to-text (Whisper) | 8000 | 8200 | No (CPU) | — |

**Volumes:**
- `./models:/models:ro` — Model files (llama, TTS, Whisper cache)
- `./config:/app/config:ro` — Constitution and configs (orchestrator)
- `./memory:/app/memory:rw` — Memory files (orchestrator)
- `./logs:/app/logs:rw` — Runtime logs (orchestrator)
- `./voices:/app/voices:rw` — Saved voice profiles (orchestrator)
- `./media:/app/media:rw` — Uploaded video files (orchestrator)
- `./services/searxng/config:/etc/searxng:rw` — SearXNG config
- `./models/whisper-cache:/root/.cache/huggingface:Z` — Whisper model cache

## Request Lifecycle

Step-by-step walkthrough: user sends "Search for AI news" with thinking mode ON.

1. User types message and clicks Send in the SvelteKit UI
2. UI sends JSON via WebSocket to `ws://gizmo-orchestrator:9100/ws/chat`
3. Message payload: `{"message": "Search for AI news", "thinking": true, "conversation_id": "uuid"}`
4. Orchestrator receives message, loads conversation history from server-side JSON file
5. Orchestrator loads constitution files, scans memory for relevant files
6. System prompt assembled: constitution-functionality + constitution-behavior rules + relevant memories
7. Messages array built in OpenAI format: `[system, ...history, user]`
8. Orchestrator POSTs to `http://gizmo-llama:8080/v1/chat/completions` with `stream: true`, `enable_thinking: true`, and tool definitions
9. Model begins generating — llama.cpp separates reasoning into `reasoning_content` field
10. Thinking tokens streamed as `{"type": "thinking", "content": "..."}` events to UI
11. Model finishes reasoning, orchestrator switches to `{"type": "token"}` events for response content
13. Model outputs tool call: `web_search({"query": "AI news"})`
14. Orchestrator sends `{"type": "tool_call", "tool": "web_search", "status": "running"}` to UI
15. Orchestrator queries SearXNG at `http://gizmo-searxng:8080/search?q=AI+news&format=json`
16. Top 5 results formatted and injected as tool result message
17. Orchestrator sends `{"type": "tool_result", "tool": "web_search", "result": "..."}` to UI
18. Orchestrator resumes llama.cpp with tool results in context
19. Final response tokens stream as `{"type": "token"}` events
20. Stream ends → orchestrator sends `{"type": "done", "trace_id": "gizmo-abc123"}`
21. UI renders complete message with collapsed thinking block above response
22. Orchestrator saves messages to server-side JSON file

## WebSocket Event Protocol

### Server → Client

| Event Type | Fields | Description |
|-----------|--------|-------------|
| `trace_id` | `trace_id` | Unique ID for this request (gizmo-{8hex}) |
| `thinking` | `content` | Thinking block content (streamed incrementally) |
| `token` | `content` | Response token (streamed incrementally) |
| `tool_call` | `tool`, `status` | Tool execution started |
| `tool_result` | `tool`, `result` | Tool execution result |
| `audio` | `url` | Audio data URL (base64 WAV) |
| `done` | `trace_id`, `conversation_id` | Generation complete |
| `error` | `error`, `trace_id` | Error occurred |

### Client → Server

```json
{
  "message": "user message text",
  "thinking": false,
  "conversation_id": "uuid-or-null",
  "tts": false
}
```

## REST API

The orchestrator exposes a non-streaming REST endpoint for programmatic access plus various management endpoints.

### `POST /api/chat`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `message` | Form string | `""` | User message text |
| `thinking` | Form bool | `false` | Enable thinking mode |
| `conversation_id` | Form string | `""` | Conversation ID (auto-generated if empty) |

**Response:**
```json
{
  "response": "assistant response text",
  "thinking": "reasoning content (if thinking enabled)",
  "conversation_id": "uuid"
}
```

Supports up to 5 rounds of automatic tool calling per request.

### All REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Orchestrator health check |
| `/api/services/health` | GET | Health of all backend services |
| `/api/conversations` | GET | List all conversations (server-side JSON) |
| `/api/conversations/{id}` | GET | Get conversation messages |
| `/api/conversations/{id}` | PUT | Update conversation |
| `/api/conversations/{id}` | DELETE | Delete a conversation |
| `/api/conversations/import` | POST | Import conversations from localStorage |
| `/api/upload` | POST | Upload document (PDF, text, code — up to 50MB) |
| `/api/upload-image` | POST | Upload image (returns base64 data URL — up to 50MB) |
| `/api/upload-video` | POST | Upload video (frame extraction + server storage — up to 500MB) |
| `/api/transcribe` | POST | Transcribe audio via Whisper |
| `/api/tts` | POST | Synthesize speech (JSON: `text`, `voice_id`) |
| `/api/voices` | GET | List saved voice profiles |
| `/api/voices` | POST | Upload and save a voice profile (FormData: file, name, max_duration) |
| `/api/voices/{id}` | DELETE | Delete a saved voice profile |
| `/api/media/{filename}` | GET | Serve uploaded video/media files |
| `/api/search` | GET | Web search via SearXNG (`?q=query`) |
| `/api/memory/list` | GET | List memory files |
| `/api/memory/write` | POST | Write memory file |

## Thinking Mode Implementation

Qwen3.5-9B is a hybrid thinking model — it always performs chain-of-thought reasoning internally. The orchestrator controls how this reasoning is exposed using llama.cpp's native `enable_thinking` API.

When `enable_thinking` is `true`, llama.cpp separates the model's internal reasoning into a dedicated `reasoning_content` field in the streaming delta. When `false`, the model still thinks internally but the reasoning is not surfaced.

The Think toggle is a pill button in the chat input area (similar to Claude and ChatGPT).

## Memory System

### File Structure
```
memory/
├── facts/          # Persistent facts (user's name, preferences)
├── conversations/  # Conversation summaries (future use)
└── notes/          # General notes
```

### Injection Logic
1. On each message, the orchestrator extracts keywords from the user's input
2. Memory files are scanned for keyword matches (filename and content)
3. Top 5 matches (max 300 chars each) are injected into the system prompt
4. Path traversal protection: filenames sanitized to `[a-zA-Z0-9_\-.]` only

## Tool Calling

Tools follow the OpenAI function-calling format. llama.cpp supports this natively.

### Available Tools

| Tool | Parameters | Description |
|------|-----------|-------------|
| `web_search` | `query: string` | Search the web via SearXNG |
| `read_memory` | `filename: string, subdir?: string` | Read a memory file |
| `write_memory` | `filename: string, content: string, subdir?: string` | Write a memory file |
| `list_memories` | `subdir?: string` | List all memory files |

### Execution Flow
1. Tool definitions are included in the API request to llama.cpp
2. Model outputs a structured tool call in the response
3. Orchestrator detects the tool call (via API `finish_reason: "tool_calls"` or JSON parsing)
4. Tool is executed asynchronously
5. Result is added to messages as a `tool` role message
6. Generation resumes with tool results in context

## Constitution System

The model's persona and behavior are defined by a split constitution system:

- **`config/constitution-functionality.txt`** — Prose base prompt defining identity and capabilities. Lines starting with `#` stripped as comments.
- **`config/constitution-behavior.txt`** — Structured rules with injection points:
  - `[system_prompt]` — Base behavior rules
  - `[pre_routing]` — Keyword-based routing overrides (`keyword => model`)
  - `[patterns]` — Pattern activation rules
  - `[per_model:name]` — Model-specific instructions
- **`config/patterns/*.md`** — Pattern library (7 patterns: extract_wisdom, analyze_threat, summarize_content, explain_technical, create_analogy, security_review, debug_code)

## Configuration Files

### models.yaml
```yaml
default_model: huihui-qwen35-9b
models:
  huihui-qwen35-9b:
    name: "Huihui-Qwen3.5-9B Abliterated"
    file: "Huihui-Qwen3.5-9B-abliterated.Q8_0.gguf"
    mmproj: "mmproj/Huihui-Qwen3.5-9B-abliterated.mmproj-Q8_0.gguf"
    architecture: qwen3_5
    parameters: 9B
    quantization: Q8_0
    context_window: 262144
    context_limit: 32768
    thinking_capable: true
    vision_capable: true
    gpu_layers: 99
    vram_required_gb: 12
```

### services.yaml
Defines all service endpoints, ports, and health check paths. Used by scripts and future service discovery.

## File Tree

```
~/gizmo-ai/
├── CLAUDE.md                              # Claude Code session knowledge
├── README.md                              # Public-facing documentation
├── AUDIT.md                               # Version audit reports
├── LICENSE                                # MIT license
├── .gitignore                             # Git ignore rules
├── docker-compose.yml                     # Podman compose — all 6 services
├── config/
│   ├── constitution-functionality.txt     # System prompt / persona (prose)
│   ├── constitution-behavior.txt          # Structured behavior rules
│   ├── patterns/                          # Pattern library (7 patterns)
│   │   ├── extract_wisdom.md
│   │   ├── analyze_threat.md
│   │   ├── summarize_content.md
│   │   ├── explain_technical.md
│   │   ├── create_analogy.md
│   │   ├── security_review.md
│   │   └── debug_code.md
│   ├── models.yaml                        # Model configuration
│   └── services.yaml                      # Service endpoints
├── services/
│   ├── llama/
│   │   └── Dockerfile                     # llama.cpp from source with CUDA
│   ├── orchestrator/
│   │   ├── Dockerfile                     # Python 3.12 slim
│   │   ├── requirements.txt               # Python dependencies
│   │   ├── main.py                        # FastAPI app, WebSocket, REST, voice/video/transcribe endpoints
│   │   ├── router.py                      # Route placeholder
│   │   ├── memory.py                      # File-based memory system
│   │   ├── search.py                      # SearXNG proxy
│   │   ├── tts.py                         # Qwen3-TTS proxy (voice cloning support)
│   │   └── tools.py                       # Tool definitions and dispatch
│   ├── ui/
│   │   ├── Dockerfile                     # Node build → nginx serve
│   │   ├── nginx.conf                     # Static + API/WS proxy (500MB upload limit)
│   │   ├── package.json                   # SvelteKit dependencies
│   │   ├── svelte.config.js               # SvelteKit + static adapter
│   │   ├── vite.config.ts                 # Vite + TailwindCSS
│   │   └── src/
│   │       ├── app.html                   # HTML shell
│   │       ├── app.css                    # TailwindCSS + design tokens
│   │       ├── routes/+page.svelte        # Main page (VoiceStudio, HTTPS banner)
│   │       ├── routes/+layout.svelte      # Root layout
│   │       └── lib/
│   │           ├── stores/chat.ts         # Conversation state (videoUrl support)
│   │           ├── stores/settings.ts     # User preferences (voiceStudioOpen)
│   │           ├── stores/connection.ts   # WebSocket state
│   │           ├── ws/client.ts           # WebSocket manager
│   │           ├── utils/sanitize.ts      # HTML sanitization
│   │           ├── actions/highlight.ts   # Code syntax highlighting
│   │           └── components/
│   │               ├── ChatArea.svelte    # Main chat area
│   │               ├── ChatInput.svelte   # Input with Think/Voice Studio pills
│   │               ├── ChatMessage.svelte # Message display (video player, audio)
│   │               ├── Header.svelte      # Top bar with settings
│   │               ├── Sidebar.svelte     # Conversation list
│   │               ├── Settings.svelte    # Settings panel
│   │               ├── VoiceStudio.svelte # Voice Studio modal
│   │               ├── ThinkingBlock.svelte # Collapsible thinking display
│   │               └── ToolCallBlock.svelte # Tool call display
│   ├── tts/
│   │   ├── Dockerfile                     # Qwen3-TTS container (PyTorch + CUDA)
│   │   ├── requirements.txt               # qwen-tts, fastapi, uvicorn
│   │   ├── main.py                        # TTS server with voice cloning
│   │   └── assets/default_voice.wav       # Default reference voice
│   └── searxng/
│       └── config/settings.yml            # SearXNG configuration
├── scripts/
│   ├── start.sh                           # Start all services (ordered)
│   ├── stop.sh                            # Stop all services
│   ├── health.sh                          # Check all service health
│   ├── build-llamacpp.sh                  # Build llama.cpp image
│   └── download-model.sh                  # Download model from HuggingFace
├── wiki/                                  # Documentation
├── models/                                # Model files (gitignored)
├── memory/                                # Persistent memory (gitignored)
├── voices/                                # Saved voice profiles (gitignored)
├── media/                                 # Uploaded videos (gitignored)
└── logs/                                  # Runtime logs (gitignored)
```
