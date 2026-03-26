<script lang="ts">
	import { createTask, createNote, activeTab } from '$lib/stores/tracker';
	import type { Task } from '$lib/stores/tracker';

	let title = $state('');
	let priority = $state<Task['priority']>('medium');

	async function submit() {
		const text = title.trim();
		if (!text) return;

		if ($activeTab === 'tasks') {
			await createTask({ title: text, priority });
		} else {
			await createNote({ title: text, content: '' });
		}
		title = '';
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			e.preventDefault();
			submit();
		}
	}

	const priorityColors: Record<string, string> = {
		urgent: 'text-error',
		high: 'text-accent',
		medium: 'text-text-secondary',
		low: 'text-text-dim',
	};
</script>

<div class="flex items-center gap-3 px-4 py-2.5 bg-bg-secondary/50 border-b border-border/30">
	<!-- Input with icon -->
	<div class="flex-1 flex items-center gap-2 bg-bg-tertiary rounded-lg px-3 py-1.5 border border-border/30 focus-within:border-accent/50 transition-colors">
		<svg class="w-4 h-4 text-text-dim shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
		</svg>
		<input
			bind:value={title}
			onkeydown={handleKeydown}
			placeholder={$activeTab === 'tasks' ? 'Add a task... (Enter to create)' : 'Add a note... (Enter to create)'}
			class="flex-1 bg-transparent text-text-primary text-sm outline-none placeholder:text-text-dim/60"
		/>
	</div>

	<!-- Priority (tasks only) -->
	{#if $activeTab === 'tasks'}
		<select
			bind:value={priority}
			class="text-xs bg-bg-tertiary {priorityColors[priority]} rounded-lg px-2.5 py-2 border border-border/30 outline-none cursor-pointer"
		>
			<option value="urgent">🔴 Urgent</option>
			<option value="high">🟠 High</option>
			<option value="medium">🟡 Medium</option>
			<option value="low">⚪ Low</option>
		</select>
	{/if}

	<!-- Submit -->
	<button
		onclick={submit}
		disabled={!title.trim()}
		class="px-4 py-1.5 text-xs font-semibold rounded-lg transition-all
			{title.trim() ? 'bg-accent text-bg-primary hover:brightness-110 shadow-sm' : 'bg-bg-tertiary text-text-dim cursor-not-allowed'}"
	>
		{$activeTab === 'tasks' ? 'Add Task' : 'Add Note'}
	</button>
</div>
