<script lang="ts">
	import { notes } from '$lib/stores/tracker';
	import NoteItem from './NoteItem.svelte';

	let searchQuery = $state('');
	let pinnedOnly = $state(false);

	let filteredNotes = $derived.by(() => {
		let result = [...$notes];
		if (searchQuery.trim()) {
			const q = searchQuery.toLowerCase();
			result = result.filter(
				(n) =>
					n.title.toLowerCase().includes(q) ||
					n.content.toLowerCase().includes(q) ||
					n.tags.some((t) => t.toLowerCase().includes(q))
			);
		}
		if (pinnedOnly) result = result.filter((n) => n.pinned);
		result.sort((a, b) => {
			if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
			return b.updated_at.localeCompare(a.updated_at);
		});
		return result;
	});

	let pinnedCount = $derived($notes.filter(n => n.pinned).length);
</script>

<!-- Search / filter bar -->
<div class="flex items-center gap-2 px-4 py-2 border-b border-border/20">
	<div class="flex-1 flex items-center gap-2 bg-bg-tertiary/50 rounded-lg px-3 py-1.5 focus-within:ring-1 focus-within:ring-accent/30 transition-all">
		<svg class="w-3.5 h-3.5 text-text-dim shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
		</svg>
		<input
			bind:value={searchQuery}
			placeholder="Search notes..."
			class="flex-1 bg-transparent text-text-primary text-sm outline-none placeholder:text-text-dim/50"
		/>
	</div>
	<button
		onclick={() => pinnedOnly = !pinnedOnly}
		class="flex items-center gap-1 px-2.5 py-1.5 text-[11px] font-medium rounded-lg transition-all
			{pinnedOnly ? 'bg-accent/15 text-accent ring-1 ring-accent/30' : 'text-text-dim hover:bg-bg-hover'}"
		aria-label="Toggle pinned filter"
	>
		<svg class="w-3 h-3" fill={pinnedOnly ? 'currentColor' : 'none'} stroke="currentColor" viewBox="0 0 24 24">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
		</svg>
		Pinned {#if pinnedCount > 0}<span class="text-[10px] opacity-60">({pinnedCount})</span>{/if}
	</button>
</div>

<!-- Note list -->
<div class="divide-y divide-border/10">
	{#each filteredNotes as note (note.id)}
		<NoteItem {note} />
	{:else}
		<div class="flex flex-col items-center justify-center py-16 text-text-dim">
			<svg class="w-10 h-10 mb-3 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
			</svg>
			<p class="text-sm">{searchQuery ? 'No matching notes' : 'No notes yet'}</p>
			<p class="text-xs mt-1 text-text-dim/60">Switch to Notes tab and add one above</p>
		</div>
	{/each}
</div>
