import { writable } from 'svelte/store';

function persistedWritable<T>(key: string, defaultValue: T) {
	const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null;
	const initial = stored !== null ? JSON.parse(stored) : defaultValue;
	const store = writable<T>(initial);
	if (typeof localStorage !== 'undefined') {
		store.subscribe((value) => localStorage.setItem(key, JSON.stringify(value)));
	}
	return store;
}

export type ThemeName = 'default' | 'nes' | 'snes' | 'n64' | 'gamecube' | 'wii' | 'switch' | 'gba' | 'ds' | '3ds';

export const theme = persistedWritable<ThemeName>('gizmo:theme', 'default');

export const themeOptions: { id: ThemeName; label: string; era: string; swatch: string }[] = [
	{ id: 'default', label: 'Default', era: 'Dark', swatch: '#d4a574' },
	{ id: 'nes', label: 'NES', era: '8-bit', swatch: '#e40000' },
	{ id: 'snes', label: 'SNES', era: '16-bit', swatch: '#c8a0ff' },
	{ id: 'gba', label: 'GBA', era: 'Handheld', swatch: '#7bb860' },
	{ id: 'n64', label: 'N64', era: '3D Era', swatch: '#e04040' },
	{ id: 'gamecube', label: 'GameCube', era: '3D Era', swatch: '#7b6fcf' },
	{ id: 'wii', label: 'Wii', era: 'Modern', swatch: '#0088cc' },
	{ id: 'ds', label: 'DS', era: 'Dual-Screen', swatch: '#3070d0' },
	{ id: '3ds', label: '3DS', era: 'Dual-Screen', swatch: '#00bcd4' },
	{ id: 'switch', label: 'Switch', era: 'Modern', swatch: '#e60012' },
];
