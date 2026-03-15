<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import Header from '$lib/components/Header.svelte';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import ChatArea from '$lib/components/ChatArea.svelte';
	import ChatInput from '$lib/components/ChatInput.svelte';
	import Settings from '$lib/components/Settings.svelte';
	import VoiceStudio from '$lib/components/VoiceStudio.svelte';
	import MemoryManager from '$lib/components/MemoryManager.svelte';
	import CodePlayground from '$lib/components/CodePlayground.svelte';
	import { connect, disconnect } from '$lib/ws/client';
	import { loadConversations } from '$lib/stores/chat';
	import { voiceStudioOpen } from '$lib/stores/settings';

	let showHttpBanner = $state(false);

	onMount(() => {
		connect();
		loadConversations();
		if (window.location.protocol === 'http:' && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
			showHttpBanner = true;
		}
	});

	onDestroy(() => {
		disconnect();
	});
</script>

<svelte:head>
	<title>Gizmo-AI</title>
</svelte:head>

<div class="flex flex-col h-screen bg-bg-primary">
	{#if showHttpBanner}
		<div class="bg-amber-500/10 border-b border-amber-500/30 px-4 py-2 text-xs text-amber-400 flex items-center justify-between">
			<span>Mic & voice features require HTTPS. Use <a href="https://bazzite.tail163501.ts.net/" class="underline hover:text-amber-300">https://bazzite.tail163501.ts.net/</a> for full access.</span>
			<button onclick={() => showHttpBanner = false} class="text-amber-500/60 hover:text-amber-400 ml-2 p-0.5" aria-label="Dismiss">
				<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" /></svg>
			</button>
		</div>
	{/if}
	<Header />
	<div class="flex flex-1 overflow-hidden">
		<Sidebar />
		<main class="flex flex-col flex-1 overflow-hidden">
			<ChatArea />
			<ChatInput />
		</main>
	</div>
</div>

<Settings />
<VoiceStudio bind:open={$voiceStudioOpen} />
<MemoryManager />
<CodePlayground />
