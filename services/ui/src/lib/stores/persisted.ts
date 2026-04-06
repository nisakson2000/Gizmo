import { writable } from 'svelte/store';

export function persistedWritable<T>(key: string, defaultValue: T) {
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
