<script lang="ts">
	import { marked } from 'marked';
	import { sanitize } from '$lib/utils/sanitize';
	import { highlightCode } from '$lib/actions/highlight';
	import ThinkingBlock from './ThinkingBlock.svelte';
	import ToolCallBlock from './ToolCallBlock.svelte';
	import type { Message } from '$lib/stores/chat';

	let { message }: { message: Message } = $props();
	let copied = $state(false);

	marked.setOptions({ breaks: true, gfm: true });

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
	<div class="flex justify-end mb-5">
		<div class="max-w-[75%] bg-user-msg rounded-2xl rounded-br-md px-4 py-2.5">
			<div class="prose-chat text-[15px]">
				{@html renderedHtml}
			</div>
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
			<span class="text-[11px] text-text-dim font-mono">
				{formatTime(message.timestamp)}
			</span>
			{#if message.traceId}
				<span class="text-[11px] text-text-dim font-mono">{message.traceId}</span>
			{/if}
		</div>
	</div>
{/if}
