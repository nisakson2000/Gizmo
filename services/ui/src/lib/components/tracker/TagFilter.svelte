<script lang="ts">
	import { allTags, taskFilter, tasks } from '$lib/stores/tracker';

	function setTag(tag: string) {
		taskFilter.update((f) => ({ ...f, tag: f.tag === tag ? '' : tag }));
	}

	function clearTag() {
		taskFilter.update((f) => ({ ...f, tag: '' }));
	}

	function getTagCount(tag: string): number {
		return $tasks.filter(t => t.tags?.includes(tag)).length;
	}
</script>

<aside class="w-44 bg-bg-secondary/50 border-r border-border/20 flex flex-col h-full shrink-0">
	<div class="px-3 py-2.5 border-b border-border/20">
		<h4 class="text-[10px] font-semibold text-text-dim uppercase tracking-widest">Filter by Tag</h4>
	</div>

	<div class="flex-1 overflow-y-auto p-1.5 space-y-0.5">
		<button
			onclick={clearTag}
			class="w-full text-left px-2.5 py-1.5 text-[13px] rounded-md transition-all flex items-center justify-between
				{!$taskFilter.tag ? 'bg-bg-hover text-text-primary font-medium' : 'text-text-secondary hover:bg-bg-hover/50'}"
		>
			<span>All tasks</span>
			<span class="text-[10px] text-text-dim">{$tasks.length}</span>
		</button>

		{#each $allTags as tag}
			{@const count = getTagCount(tag)}
			<button
				onclick={() => setTag(tag)}
				class="w-full text-left px-2.5 py-1.5 text-[13px] rounded-md transition-all flex items-center gap-2
					{$taskFilter.tag === tag ? 'bg-accent/10 text-accent font-medium' : 'text-text-secondary hover:bg-bg-hover/50'}"
			>
				<span class="w-1.5 h-1.5 rounded-full shrink-0 {$taskFilter.tag === tag ? 'bg-accent' : 'bg-text-dim/30'}"></span>
				<span class="truncate flex-1">{tag}</span>
				<span class="text-[10px] text-text-dim/60">{count}</span>
			</button>
		{/each}

		{#if $allTags.length === 0}
			<p class="text-[11px] text-text-dim/50 px-2.5 py-3 text-center">No tags yet.<br/>Add tags when creating tasks.</p>
		{/if}
	</div>
</aside>
