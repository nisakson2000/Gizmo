import { writable } from 'svelte/store';

export const thinkingEnabled = writable(false);
export const ttsEnabled = writable(false);
export const contextLength = writable(32768);
export const sidebarOpen = writable(true);
export const settingsOpen = writable(false);
