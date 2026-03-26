<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { marked } from 'marked';
	import { sanitize } from '$lib/utils/sanitize';
	import { trackerChatOpen } from '$lib/stores/tracker';
	import {
		trackerMessages,
		trackerGenerating,
		trackerStreamingContent,
		trackerStreamingThinking,
		sendTrackerMessage,
	} from '$lib/ws/tracker-client';

	let inputText = $state('');
	let messagesEl: HTMLDivElement | undefined = $state();

	marked.setOptions({ breaks: true, gfm: true });

	function renderMarkdown(text: string): string {
		try {
			return sanitize(marked.parse(text) as string);
		} catch {
			return text;
		}
	}

	function handleSend() {
		if (!inputText.trim() || $trackerGenerating) return;
		sendTrackerMessage(inputText.trim());
		inputText = '';
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter' && !e.shiftKey) {
			e.preventDefault();
			handleSend();
		}
	}

	function toggleChat() {
		trackerChatOpen.update((v) => !v);
	}

	// Auto-scroll on new messages
	$effect(() => {
		// Access reactive dependencies
		$trackerMessages;
		$trackerStreamingContent;
		tick().then(() => {
			if (messagesEl) {
				messagesEl.scrollTop = messagesEl.scrollHeight;
			}
		});
	});
</script>

{#if !$trackerChatOpen}
	<!-- Collapsed: floating button -->
	<button
		onclick={toggleChat}
		class="fixed bottom-4 right-4 z-10 bg-accent text-bg-primary px-4 py-2.5 rounded-lg shadow-lg
			hover:bg-accent-dim transition-colors text-sm font-medium flex items-center gap-2"
	>
		<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" />
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M2.25 12.76c0 1.6 1.123 2.994 2.707 3.227 1.087.16 2.185.283 3.293.369V21l4.076-4.076a1.526 1.526 0 011.037-.443 48.282 48.282 0 005.68-.494c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0012 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018z" />
		</svg>
		Ask Gizmo
	</button>
{:else}
	<!-- Expanded: chat panel -->
	<div class="w-80 bg-bg-secondary border-l border-border/40 flex flex-col h-full">
		<!-- Header -->
		<div class="flex items-center justify-between px-3 py-2.5 border-b border-border/40">
			<span class="text-sm font-medium text-text-primary">Tracker Chat</span>
			<button
				onclick={toggleChat}
				class="text-text-dim hover:text-text-secondary transition-colors"
				aria-label="Collapse chat"
			>
				<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
				</svg>
			</button>
		</div>

		<!-- Messages -->
		<div bind:this={messagesEl} class="flex-1 overflow-y-auto p-3 space-y-3">
			{#each $trackerMessages as msg (msg.id)}
				<div class="flex flex-col gap-1 {msg.role === 'user' ? 'items-end' : 'items-start'}">
					{#if msg.role === 'user'}
						<div class="bg-user-msg text-text-primary text-sm rounded-lg px-3 py-2 max-w-[90%]">
							{msg.content}
						</div>
					{:else}
						{#if msg.thinking}
							<div class="text-[10px] text-text-dim italic bg-thinking border-l-2 border-thinking-border rounded px-2 py-1 max-w-[90%]">
								{msg.thinking.length > 120 ? msg.thinking.slice(0, 120) + '...' : msg.thinking}
							</div>
						{/if}
						{#if msg.toolCalls}
							{#each msg.toolCalls as tc}
								<div class="text-[10px] text-text-dim flex items-center gap-1 px-1">
									<span class="w-1.5 h-1.5 rounded-full {tc.status === 'done' ? 'bg-success' : 'bg-accent'}"></span>
									{tc.tool}
								</div>
							{/each}
						{/if}
						<div class="prose-sm text-text-primary text-sm max-w-[90%] [&_p]:my-1 [&_ul]:my-1 [&_ol]:my-1 [&_code]:bg-code-bg [&_code]:px-1 [&_code]:rounded [&_code]:text-xs">
							{@html renderMarkdown(msg.content)}
						</div>
					{/if}
				</div>
			{/each}

			<!-- Streaming content -->
			{#if $trackerGenerating}
				<div class="flex flex-col gap-1 items-start">
					{#if $trackerStreamingThinking}
						<div class="text-[10px] text-text-dim italic bg-thinking border-l-2 border-thinking-border rounded px-2 py-1 max-w-[90%]">
							{$trackerStreamingThinking.length > 120 ? $trackerStreamingThinking.slice(0, 120) + '...' : $trackerStreamingThinking}
						</div>
					{/if}
					{#if $trackerStreamingContent}
						<div class="prose-sm text-text-primary text-sm max-w-[90%] [&_p]:my-1 [&_ul]:my-1 [&_ol]:my-1 [&_code]:bg-code-bg [&_code]:px-1 [&_code]:rounded [&_code]:text-xs">
							{@html renderMarkdown($trackerStreamingContent)}
						</div>
					{:else}
						<div class="flex gap-1 px-2 py-2">
							<span class="w-1.5 h-1.5 rounded-full bg-text-dim animate-pulse"></span>
							<span class="w-1.5 h-1.5 rounded-full bg-text-dim animate-pulse" style="animation-delay: 0.15s"></span>
							<span class="w-1.5 h-1.5 rounded-full bg-text-dim animate-pulse" style="animation-delay: 0.3s"></span>
						</div>
					{/if}
				</div>
			{/if}
		</div>

		<!-- Input -->
		<div class="border-t border-border/40 p-2">
			<div class="flex items-end gap-2">
				<textarea
					bind:value={inputText}
					onkeydown={handleKeydown}
					placeholder="Ask about tasks, notes..."
					rows={1}
					class="flex-1 bg-bg-tertiary text-text-primary text-sm rounded px-3 py-2 border border-border/40 outline-none focus:border-accent/60 resize-none max-h-24"
				></textarea>
				<button
					onclick={handleSend}
					disabled={!inputText.trim() || $trackerGenerating}
					class="p-2 rounded transition-colors flex-shrink-0
						{inputText.trim() && !$trackerGenerating ? 'bg-accent text-bg-primary hover:bg-accent-dim' : 'bg-bg-tertiary text-text-dim cursor-not-allowed'}"
					aria-label="Send"
				>
					<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19V5m0 0l-7 7m7-7l7 7" />
					</svg>
				</button>
			</div>
		</div>
	</div>
{/if}
