import { writable } from 'svelte/store';

export type ConnectionStatus = 'connected' | 'disconnected' | 'connecting' | 'generating';
export const connectionStatus = writable<ConnectionStatus>('disconnected');
