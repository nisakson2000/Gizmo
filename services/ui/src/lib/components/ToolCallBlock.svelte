<script lang="ts">
	let { tool, status, result }: { tool: string; status: string; result?: string } = $props();
	let expanded = $state(false);

	const toolLabels: Record<string, { running: string; done: string }> = {
		web_search: { running: 'Searching the web...', done: 'Web search complete' },
		read_memory: { running: 'Reading memory...', done: 'Memory retrieved' },
		write_memory: { running: 'Saving to memory...', done: 'Memory saved' },
		list_memories: { running: 'Checking memories...', done: 'Memories checked' },
		run_code: { running: 'Running code...', done: 'Code execution complete' },
	};

	let label = $derived(
		status === 'done'
			? (toolLabels[tool]?.done || `${tool} complete`)
			: (toolLabels[tool]?.running || `Running ${tool}...`)
	);
</script>

<div class="mb-2 rounded border border-border bg-bg-tertiary/50">
	<button
		onclick={() => { if (result) expanded = !expanded; }}
		class="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-text-secondary hover:text-text-primary transition-colors"
	>
		{#if status !== 'done'}
			<svg class="w-3.5 h-3.5 text-accent animate-spin" fill="none" viewBox="0 0 24 24">
				<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
				<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
			</svg>
		{:else}
			<svg class="w-3.5 h-3.5 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
			</svg>
		{/if}
		<span>{label}</span>
		{#if result}
			<svg
				class="w-3 h-3 ml-auto transition-transform {expanded ? 'rotate-180' : ''}"
				fill="none"
				stroke="currentColor"
				viewBox="0 0 24 24"
			>
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
			</svg>
		{/if}
	</button>

	{#if expanded && result}
		<div class="px-3 pb-2 text-xs text-text-secondary font-mono whitespace-pre-wrap leading-relaxed max-h-48 overflow-y-auto border-t border-border/50">
			{result}
		</div>
	{/if}
</div>
