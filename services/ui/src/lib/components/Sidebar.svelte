<script lang="ts">
	import {
		conversations,
		activeConversationId,
		newConversation,
		loadConversation,
		deleteConversation,
	} from '$lib/stores/chat';
	import { sidebarOpen, settingsOpen } from '$lib/stores/settings';

	let search = $state('');

	let filtered = $derived(
		$conversations.filter((c) => c.title.toLowerCase().includes(search.toLowerCase()))
	);

	interface DateGroup {
		label: string;
		convs: typeof $conversations;
	}

	let grouped = $derived.by((): DateGroup[] => {
		const now = new Date();
		const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
		const yesterday = new Date(today.getTime() - 86400000);
		const weekAgo = new Date(today.getTime() - 7 * 86400000);

		const groups: DateGroup[] = [
			{ label: 'Today', convs: [] },
			{ label: 'Yesterday', convs: [] },
			{ label: 'Previous 7 Days', convs: [] },
			{ label: 'Older', convs: [] },
		];

		for (const conv of filtered) {
			const d = new Date(conv.updated_at);
			if (d >= today) groups[0].convs.push(conv);
			else if (d >= yesterday) groups[1].convs.push(conv);
			else if (d >= weekAgo) groups[2].convs.push(conv);
			else groups[3].convs.push(conv);
		}

		return groups.filter((g) => g.convs.length > 0);
	});

	function handleConvClick(id: string) {
		loadConversation(id);
	}

	function handleDeleteClick(e: MouseEvent, id: string) {
		e.stopPropagation();
		deleteConversation(id);
	}
</script>

{#if $sidebarOpen}
	<aside class="w-60 flex-shrink-0 bg-bg-secondary/50 border-r border-border/40 flex flex-col h-full">
		<div class="p-3">
			<button
				onclick={() => newConversation()}
				class="w-full px-3 py-2 rounded-lg bg-bg-hover/60 border border-border/40 text-text-secondary hover:text-text-primary hover:border-border text-sm transition-all text-left flex items-center gap-2"
			>
				<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 4.5v15m7.5-7.5h-15" />
				</svg>
				New chat
			</button>
		</div>

		<div class="px-3 pb-2">
			<input
				type="text"
				placeholder="Search..."
				bind:value={search}
				class="w-full px-2.5 py-1.5 bg-bg-primary/50 border border-border/30 rounded-lg text-sm text-text-primary placeholder:text-text-dim focus:outline-none focus:border-border transition-colors"
			/>
		</div>

		<div class="flex-1 overflow-y-auto px-2">
			{#each grouped as group}
				<div class="px-2 pt-4 pb-1 text-[11px] uppercase tracking-wider text-text-dim/70 font-medium">
					{group.label}
				</div>
				{#each group.convs as conv (conv.id)}
					<!-- svelte-ignore a11y_click_events_have_key_events -->
					<!-- svelte-ignore a11y_no_static_element_interactions -->
					<div
						onclick={() => handleConvClick(conv.id)}
						class="w-full text-left px-3 py-2 rounded-lg mb-0.5 flex items-center justify-between group transition-colors cursor-pointer
							{$activeConversationId === conv.id
								? 'bg-bg-hover text-text-primary'
								: 'text-text-secondary hover:bg-bg-hover/50 hover:text-text-primary'}"
					>
						<span class="truncate text-sm flex-1">{conv.title}</span>
						<button
							onclick={(e) => handleDeleteClick(e, conv.id)}
							class="ml-1 text-text-dim hover:text-error opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
							aria-label="Delete conversation"
						>
							<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
								<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
							</svg>
						</button>
					</div>
				{/each}
			{/each}
		</div>

		<div class="p-3 border-t border-border/30">
			<button
				onclick={() => settingsOpen.update((v) => !v)}
				class="w-full px-3 py-2 rounded-lg text-sm text-text-dim hover:bg-bg-hover/50 hover:text-text-secondary transition-colors text-left flex items-center gap-2"
			>
				<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28z" />
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
				</svg>
				Settings
			</button>
		</div>
	</aside>
{/if}
