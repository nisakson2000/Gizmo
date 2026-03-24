<script lang="ts">
	import { marked } from 'marked';
	import { sanitize } from '$lib/utils/sanitize';
	import { highlightCode } from '$lib/actions/highlight';
	import ThinkingBlock from './ThinkingBlock.svelte';
	import ToolCallBlock from './ToolCallBlock.svelte';
	import { messages, generating, truncateMessagesFrom, activeConversationId, addUserMessage } from '$lib/stores/chat';
	import { send } from '$lib/ws/client';
	import { get } from 'svelte/store';
	import type { Message } from '$lib/stores/chat';

	let { message }: { message: Message } = $props();
	let copied = $state(false);
	let editing = $state(false);
	let editText = $state('');

	marked.setOptions({ breaks: true, gfm: true });

	let isLastAssistant = $derived.by(() => {
		const msgs = get(messages);
		for (let i = msgs.length - 1; i >= 0; i--) {
			if (msgs[i].role === 'assistant') return msgs[i].id === message.id;
		}
		return false;
	});

	let renderedHtml = $derived.by(() => {
		try {
			return sanitize(marked.parse(message.content) as string);
		} catch {
			return message.content;
		}
	});

	async function copyMessage() {
		await navigator.clipboard.writeText(message.content);
		copied = true;
		setTimeout(() => (copied = false), 2000);
	}

	async function regenerate() {
		const msgs = get(messages);
		const idx = msgs.findIndex((m) => m.id === message.id);
		if (idx < 0) return;
		// Find the user message before this assistant message
		let userMsg: Message | null = null;
		for (let i = idx - 1; i >= 0; i--) {
			if (msgs[i].role === 'user') { userMsg = msgs[i]; break; }
		}
		if (!userMsg) return;
		const ok = await truncateMessagesFrom(idx);
		if (!ok) return;
		messages.update((m) => m.slice(0, idx));
		send(userMsg.content, userMsg.imageUrl, userMsg.videoUrl ? undefined : undefined);
	}

	function startEdit() {
		editText = message.content;
		editing = true;
	}

	function cancelEdit() {
		editing = false;
		editText = '';
	}

	async function saveEdit() {
		const msgs = get(messages);
		const idx = msgs.findIndex((m) => m.id === message.id);
		if (idx < 0) return;
		const text = editText.trim();
		if (!text) return;
		const ok = await truncateMessagesFrom(idx);
		if (!ok) return;
		messages.update((m) => m.slice(0, idx));
		addUserMessage(text);
		send(text);
		editing = false;
		editText = '';
	}

	function handleEditKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			cancelEdit();
		}
	}

	function formatTime(ts: string): string {
		try {
			return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
		} catch {
			return '';
		}
	}
</script>

{#if message.role === 'user'}
	<!-- User message: right-aligned, accent tinted -->
	<div class="flex justify-end mb-5 group">
		<div class="max-w-[75%]">
			<div class="bg-user-msg rounded-2xl rounded-br-md px-4 py-2.5">
				{#if message.videoUrl}
					<video
						src={message.videoUrl}
						controls
						class="max-w-full max-h-64 rounded-lg mb-2"
					>
						<track kind="captions" />
					</video>
				{:else if message.imageUrl}
					<img
						src={message.imageUrl}
						alt="Uploaded image"
						class="max-w-full max-h-64 rounded-lg mb-2"
					/>
				{/if}
				{#if editing}
					<textarea
						bind:value={editText}
						onkeydown={handleEditKeydown}
						class="w-full bg-bg-primary border border-border/60 rounded-lg px-3 py-2 text-text-primary text-[15px] leading-[1.5] resize-none focus:outline-none focus:border-accent/40 min-h-[60px]"
						rows="3"
					></textarea>
					<div class="flex gap-2 mt-2">
						<button onclick={saveEdit} class="px-3 py-1 bg-accent text-white text-xs rounded-lg hover:bg-accent-dim transition-colors">Save</button>
						<button onclick={cancelEdit} class="px-3 py-1 bg-bg-tertiary text-text-secondary text-xs rounded-lg hover:bg-bg-hover transition-colors">Cancel</button>
					</div>
				{:else}
					<div class="prose-chat text-[15px]">
						{@html renderedHtml}
					</div>
				{/if}
			</div>
			{#if !editing && !$generating}
				<div class="flex justify-end mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
					<button
						onclick={startEdit}
						class="text-xs text-text-dim hover:text-text-secondary transition-colors flex items-center gap-1"
					>
						<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125" />
						</svg>
						Edit
					</button>
				</div>
			{/if}
		</div>
	</div>
{:else}
	<!-- Assistant message: full-width, no bubble -->
	<div class="mb-6 group">
		{#if message.thinking}
			<ThinkingBlock content={message.thinking} />
		{/if}

		{#if message.toolCalls?.length}
			{#each message.toolCalls as tc, i (i)}
				<ToolCallBlock tool={tc.tool} status={tc.status} result={tc.result} />
			{/each}
		{/if}

		<div class="prose-chat" use:highlightCode={renderedHtml}>
			{@html renderedHtml}
		</div>

		{#if message.audioUrl}
			<div class="mt-3">
				<audio controls src={message.audioUrl} class="w-full h-9 rounded-lg" aria-label="TTS audio">
					<track kind="captions" src="" default />
				</audio>
			</div>
		{/if}

		<!-- Actions row: visible on hover -->
		<div class="flex items-center gap-3 mt-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
			<button
				onclick={copyMessage}
				class="text-xs text-text-dim hover:text-text-secondary transition-colors flex items-center gap-1"
			>
				{#if copied}
					<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
					</svg>
					Copied
				{:else}
					<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
					</svg>
					Copy
				{/if}
			</button>
			{#if isLastAssistant && !$generating}
				<button
					onclick={regenerate}
					class="text-xs text-text-dim hover:text-text-secondary transition-colors flex items-center gap-1"
				>
					<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
					</svg>
					Regenerate
				</button>
			{/if}
			<span class="text-[11px] text-text-dim font-mono">
				{formatTime(message.timestamp)}
			</span>
			{#if message.traceId}
				<span class="text-[11px] text-text-dim font-mono">{message.traceId}</span>
			{/if}
		</div>
	</div>
{/if}
