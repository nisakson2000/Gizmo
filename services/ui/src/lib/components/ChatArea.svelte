<script lang="ts">
	import { marked } from 'marked';
	import { messages, generating, streamingThinking, streamingContent, streamingToolCalls } from '$lib/stores/chat';
	import { sanitize } from '$lib/utils/sanitize';
	import { highlightCode } from '$lib/actions/highlight';
	import ChatMessage from './ChatMessage.svelte';
	import ThinkingBlock from './ThinkingBlock.svelte';
	import ToolCallBlock from './ToolCallBlock.svelte';

	marked.setOptions({ breaks: true, gfm: true });

	let chatContainer: HTMLDivElement;
	let userScrolled = $state(false);
	let parsedStreamingHtml = $state('');
	let rafId: number | null = null;

	function scrollToBottom() {
		if (chatContainer && !userScrolled) {
			chatContainer.scrollTop = chatContainer.scrollHeight;
		}
	}

	function handleScroll() {
		if (!chatContainer) return;
		const { scrollTop, scrollHeight, clientHeight } = chatContainer;
		userScrolled = scrollHeight - scrollTop - clientHeight > 100;
	}

	// Throttled markdown parsing for streaming content
	$effect(() => {
		const raw = $streamingContent;
		if (!raw) {
			parsedStreamingHtml = '';
			return;
		}
		if (rafId) cancelAnimationFrame(rafId);
		rafId = requestAnimationFrame(() => {
			try {
				parsedStreamingHtml = sanitize(marked.parse(raw) as string);
			} catch {
				parsedStreamingHtml = raw;
			}
			rafId = null;
		});
	});

	// Auto-scroll on new content
	$effect(() => {
		$messages;
		$streamingContent;
		$streamingThinking;
		scrollToBottom();
	});
</script>

<div
	bind:this={chatContainer}
	onscroll={handleScroll}
	class="flex-1 overflow-y-auto"
>
	{#if $messages.length === 0 && !$generating}
		<!-- Empty state -->
		<div class="flex flex-col items-center justify-center h-full text-center px-4">
			<div class="w-12 h-12 rounded-2xl bg-accent/15 flex items-center justify-center mb-4">
				<svg class="w-6 h-6 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z" />
				</svg>
			</div>
			<h1 class="text-2xl font-semibold text-text-primary mb-1">Gizmo</h1>
			<p class="text-sm text-text-secondary mb-8">Local AI — no cloud, no limits</p>
			<div class="grid grid-cols-2 gap-2 max-w-md text-sm">
				<div class="bg-bg-secondary/60 border border-border/50 rounded-xl px-4 py-3 text-left hover:border-border transition-colors cursor-default">
					<p class="text-text-dim text-xs mb-1">Chat</p>
					<p class="text-text-secondary text-[13px]">Ask anything — code, writing, analysis</p>
				</div>
				<div class="bg-bg-secondary/60 border border-border/50 rounded-xl px-4 py-3 text-left hover:border-border transition-colors cursor-default">
					<p class="text-text-dim text-xs mb-1">Search</p>
					<p class="text-text-secondary text-[13px]">Search the web in real-time</p>
				</div>
				<div class="bg-bg-secondary/60 border border-border/50 rounded-xl px-4 py-3 text-left hover:border-border transition-colors cursor-default">
					<p class="text-text-dim text-xs mb-1">Remember</p>
					<p class="text-text-secondary text-[13px]">Persistent memory across chats</p>
				</div>
				<div class="bg-bg-secondary/60 border border-border/50 rounded-xl px-4 py-3 text-left hover:border-border transition-colors cursor-default">
					<p class="text-text-dim text-xs mb-1">Think</p>
					<p class="text-text-secondary text-[13px]">Step-by-step reasoning mode</p>
				</div>
			</div>
		</div>
	{:else}
		<div class="max-w-3xl mx-auto px-4 py-6">
			{#each $messages as message (message.id)}
				<div class="msg-appear">
					<ChatMessage {message} />
				</div>
			{/each}

			{#if $generating}
				<div class="mb-6 msg-appear">
					{#if $streamingThinking}
						<ThinkingBlock content={$streamingThinking} streaming={true} />
					{/if}

					{#each $streamingToolCalls as tc, i (i)}
						<ToolCallBlock tool={tc.tool} status={tc.status} result={tc.result} />
					{/each}

					{#if parsedStreamingHtml}
						<div class="prose-chat" use:highlightCode={parsedStreamingHtml}>
							{@html parsedStreamingHtml}
							<span class="streaming-cursor">|</span>
						</div>
					{:else if !$streamingThinking && $streamingToolCalls.length === 0}
						<div class="flex gap-1 py-2">
							<div class="w-1.5 h-1.5 bg-text-dim rounded-full animate-bounce" style="animation-delay: 0ms"></div>
							<div class="w-1.5 h-1.5 bg-text-dim rounded-full animate-bounce" style="animation-delay: 150ms"></div>
							<div class="w-1.5 h-1.5 bg-text-dim rounded-full animate-bounce" style="animation-delay: 300ms"></div>
						</div>
					{/if}
				</div>
			{/if}
		</div>
	{/if}
</div>
