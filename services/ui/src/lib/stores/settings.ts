import { writable } from 'svelte/store';

function persistedWritable<T>(key: string, defaultValue: T) {
	const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null;
	let initial = defaultValue;
	if (stored !== null) {
		try { initial = JSON.parse(stored); } catch { /* corrupt localStorage, use default */ }
	}
	const store = writable<T>(initial);
	if (typeof localStorage !== 'undefined') {
		store.subscribe((value) => localStorage.setItem(key, JSON.stringify(value)));
	}
	return store;
}

export const thinkingEnabled = persistedWritable('gizmo:thinking', false);
export const ttsEnabled = persistedWritable('gizmo:tts', false);
export const contextLength = persistedWritable('gizmo:contextLength', 32768);
export const sidebarOpen = persistedWritable('gizmo:sidebar', true);
export const settingsOpen = writable(false);
export const pendingSuggestion = writable('');
export const voiceStudioOpen = writable(false);
export const memoryManagerOpen = writable(false);
export const codePlaygroundOpen = writable(false);
export const ttsVoiceId = persistedWritable<string | null>('gizmo:ttsVoiceId', null);
export const ttsSpeed = persistedWritable<number>('gizmo:ttsSpeed', 1.0);
export const ttsLanguage = persistedWritable<string>('gizmo:ttsLanguage', 'Auto');
export const focusTrigger = writable(0);
