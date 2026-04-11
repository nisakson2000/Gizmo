/** Suggestion card action types — used by ChatArea and ChatInput. */

export type SuggestionAction =
	| { type: 'upload'; accept: string; prompt?: string }
	| { type: 'think'; stub: string }
	| { type: 'voice_studio' }
	| { type: 'prompt'; text: string };

export interface SuggestionCard {
	icon: string;
	label: string;
	action: SuggestionAction;
	desc: string;
}

export const UPLOAD_ACCEPT_DOCS = '.pdf,.txt,.md,.py,.js,.ts,.json,.yaml,.yml,.toml,.csv,.html,.xml,.zip';

export const suggestions: SuggestionCard[] = [
	{ icon: 'eye', label: 'Vision', action: { type: 'upload', accept: 'image/*', prompt: 'Describe what you see in the image I upload' }, desc: 'Analyze images, screenshots, diagrams' },
	{ icon: 'video', label: 'Video', action: { type: 'upload', accept: 'video/*', prompt: 'Describe what happens in this video' }, desc: 'Upload videos for frame-by-frame analysis' },
	{ icon: 'audio', label: 'Audio', action: { type: 'upload', accept: 'audio/*', prompt: 'Transcribe and analyze this audio' }, desc: 'Transcribe & analyze audio files' },
	{ icon: 'search', label: 'Search', action: { type: 'prompt', text: 'Search the web for the latest news today' }, desc: 'Real-time web search via SearXNG' },
	{ icon: 'brain', label: 'Reason', action: { type: 'think', stub: 'Think through ' }, desc: 'Extended thinking for complex problems' },
	{ icon: 'code', label: 'Code', action: { type: 'prompt', text: 'Write code that ' }, desc: 'Ask Gizmo to write and run code' },
	{ icon: 'mic', label: 'Voice Studio', action: { type: 'voice_studio' }, desc: 'Clone voices & text-to-speech' },
	{ icon: 'file', label: 'Files', action: { type: 'upload', accept: UPLOAD_ACCEPT_DOCS, prompt: 'Summarize the document I upload' }, desc: 'Upload PDFs, code, text for analysis' },
];
