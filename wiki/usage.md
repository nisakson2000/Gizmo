# Usage Guide

Day-to-day guide for using Gizmo-AI. Assumes setup is complete and services are running.

---

## Chat Basics

- **Send a message:** Type in the input box and press **Enter**
- **New line:** Press **Shift+Enter**
- **Stop generation:** Click the stop button that appears during generation
- **New conversation:** Click "+ New Chat" in the sidebar

## Thinking Mode

Toggle thinking mode with the **Think** pill button below the input box (similar to Claude and ChatGPT).

**When to use it:**
- Complex reasoning (math, logic, strategy)
- Debugging code
- Multi-step analysis
- When accuracy matters more than speed

**When NOT to use it:**
- Simple factual questions
- Casual conversation
- Quick code generation

When thinking is ON, the model reasons internally in a collapsible block before responding. The thinking block appears above the response and can be expanded to see the model's chain of thought. Thinking mode adds latency — the model generates the thinking tokens in addition to the response.

## Vision / Image Analysis

Gizmo can see and analyze images. Click the **paperclip button** to upload an image, then ask about it:

- "What's in this image?"
- "Describe the architecture in this diagram"
- "What code errors do you see in this screenshot?"

Supported formats: PNG, JPG, GIF, WebP (up to 50MB). The image is encoded and sent to the vision-capable model via the multimodal projector (mmproj).

## Video Analysis

Upload a video file and Gizmo will extract frames and analyze the visual content.

- Click the **paperclip button** and select a video file (up to 500MB)
- Frames are extracted automatically via ffmpeg
- The video appears as a playable player in your chat message
- Ask questions about what's happening in the video

Supported formats: MP4, MOV, AVI, WebM, MKV.

## Audio Transcription

Upload an audio file for automatic transcription and analysis.

- Click the **paperclip button** and select an audio file
- Gizmo transcribes it via Whisper, then analyzes the transcript with the LLM
- Great for analyzing podcasts, meetings, voice memos, lectures

Supported formats: M4A, MP3, WAV, OGG, FLAC.

## Speech-to-Text (Microphone)

Click the **microphone button** in the input area to dictate your message.

- Works with the built-in mic or any connected audio device
- Audio is transcribed via Whisper and inserted as your message text
- **Requires HTTPS** for browser mic access — use `https://bazzite.tail163501.ts.net/` from other devices on your tailnet, or `localhost` which is always considered secure

## Voice Studio

Open the **Voice Studio** via the pill button below the input box or from Settings.

Voice Studio is a dedicated TTS playground where you can:

1. **Upload voice references** — provide a sample of any voice
2. **Name and save voices** — build a library of cloned voices
3. **Select a voice** — choose which saved voice to use for synthesis
4. **Set clip duration** — choose how much of the reference audio to use (30s, 60s, 90s, or 120s)
5. **Type and speak** — enter text and hear it spoken in the selected voice

Voice references are processed server-side: truncated to the selected duration, downsampled to 16kHz mono WAV to prevent VRAM issues. Saved voices persist across sessions.

## Text-to-Speech

Toggle TTS in Settings under "Read Responses Aloud."

When enabled, Gizmo speaks responses aloud. An audio player appears below each assistant message. The TTS engine is Qwen3-TTS — a GPU-accelerated neural voice cloning model. It loads into VRAM on demand and auto-unloads after 60 seconds of idle time to free memory for the LLM.

The voice used for chat TTS is the default bundled voice. To use cloned voices, use the Voice Studio.

## Web Search

Gizmo searches the web via a self-hosted SearXNG instance. Trigger it naturally:

- "Search for the latest news about AI"
- "What's the current weather in Seattle?"
- "Find information about Rust async runtimes"

The model decides when to search based on context. When it does:
1. You'll see a "web_search: running" status
2. SearXNG returns the top 5 results
3. Results are injected into the conversation context
4. The model responds with knowledge of the search results

No API keys or accounts needed — SearXNG aggregates results from multiple search engines.

## File Uploads

Click the **paperclip button** to upload files.

**Supported types and limits:**

| Type | Formats | Max Size | Processing |
|------|---------|----------|------------|
| **Images** | PNG, JPG, GIF, WebP | 50MB | Base64-encoded, analyzed by vision model |
| **Video** | MP4, MOV, AVI, WebM, MKV | 500MB | Frame extraction, server storage, video player in chat |
| **Audio** | M4A, MP3, WAV, OGG, FLAC | 50MB | Whisper transcription → LLM analysis |
| **Documents** | PDF, TXT, MD, code files | 50MB | Text extracted and sent to model |

## Memory

Gizmo can remember things across conversations.

**To save a memory:**
- "Remember that my name is Nick"
- "Remember that I prefer Python over JavaScript"
- "Save a note: project deadline is March 20"

**To recall:**
- "What's my name?"
- "What do you remember about me?"
- "List what you remember"

Memories persist across conversations and browser sessions. They are simple text files injected into the system prompt based on keyword relevance.

## Conversation Management

- **Sidebar** shows all past conversations sorted by recency
- **Click a conversation** to load its history
- **Search** conversations using the search box in the sidebar
- **Delete** a conversation with the X button (hover to reveal)
- **New Chat** starts a fresh conversation with no history

Conversations are stored as server-side JSON files, accessible from any device on your network (not limited to a single browser origin like localStorage).

## Settings

Access via the **gear icon** in the header.

| Setting | Description |
|---------|-------------|
| **Read Responses Aloud** | Toggle spoken responses ON/OFF (Qwen3-TTS, GPU-accelerated) |
| **Voice Studio** | Shortcut to open Voice Studio |
| **Context Length** | Slider: 2,048–32,768 tokens. UI-only — model always uses 32,768 configured in compose. |
| **Service Health** | Live status of all backend services |

## Remote Access via Tailscale

Gizmo is accessible from any device on your Tailscale network.

- **HTTP**: `http://{tailscale-ip}:3100` — works for chat, but mic access requires HTTPS
- **HTTPS**: `https://bazzite.tail163501.ts.net/` — valid Let's Encrypt certificate via `tailscale serve`, enables microphone access from laptops/phones

Set up HTTPS:
```bash
tailscale serve --https=443 http://127.0.0.1:3100
```
