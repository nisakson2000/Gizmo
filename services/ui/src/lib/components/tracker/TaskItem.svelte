<script lang="ts">
	import { selectedTaskId, completeTask, updateTask, deleteTask, tasks } from '$lib/stores/tracker';
	import type { Task } from '$lib/stores/tracker';

	let { task, subtasks = [], depth = 0 }: { task: Task; subtasks?: Task[]; depth?: number } = $props();

	const priorityDot: Record<string, string> = {
		urgent: 'bg-error',
		high: 'bg-accent',
		medium: 'bg-yellow-400',
		low: 'bg-text-dim/40',
	};

	const statusIcons: Record<string, string> = {
		todo: '○',
		in_progress: '◑',
		done: '●',
		blocked: '✕',
	};

	let isOverdue = $derived(
		task.due_date && task.status !== 'done' && new Date(task.due_date) < new Date()
	);

	let isSelected = $derived($selectedTaskId === task.id);

	async function cycleStatus() {
		if (task.status === 'todo') {
			await updateTask(task.id, { status: 'in_progress' });
		} else if (task.status === 'in_progress') {
			await completeTask(task.id);
		} else if (task.status === 'done') {
			await updateTask(task.id, { status: 'todo' });
		} else if (task.status === 'blocked') {
			await updateTask(task.id, { status: 'todo' });
		}
	}

	function getChildSubtasks(parentId: string): Task[] {
		return $tasks.filter(t => t.parent_id === parentId);
	}

	function formatDate(d: string): string {
		const date = new Date(d);
		const now = new Date();
		const diff = Math.floor((date.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
		if (diff === 0) return 'Today';
		if (diff === 1) return 'Tomorrow';
		if (diff === -1) return 'Yesterday';
		if (diff > 0 && diff < 7) return date.toLocaleDateString(undefined, { weekday: 'short' });
		return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
	}
</script>

<div
	class="group flex items-center gap-2 px-4 py-2.5 hover:bg-bg-hover/50 transition-colors cursor-pointer
		{isSelected ? 'bg-bg-hover/70 border-l-2 border-accent' : 'border-l-2 border-transparent'}"
	style="padding-left: {16 + depth * 20}px"
	role="button"
	tabindex="0"
	onclick={() => selectedTaskId.set(isSelected ? null : task.id)}
	onkeydown={(e) => e.key === 'Enter' && selectedTaskId.set(isSelected ? null : task.id)}
>
	<!-- Status circle -->
	<button
		onclick={(e) => { e.stopPropagation(); cycleStatus(); }}
		class="shrink-0 w-5 h-5 flex items-center justify-center text-xs rounded-full transition-all hover:scale-110
			{task.status === 'done' ? 'text-success' : task.status === 'in_progress' ? 'text-accent' : task.status === 'blocked' ? 'text-error' : 'text-text-dim'}"
		title="Click to change status"
	>
		{statusIcons[task.status]}
	</button>

	<!-- Priority dot -->
	<div class="shrink-0 w-2 h-2 rounded-full {priorityDot[task.priority]}" title={task.priority}></div>

	<!-- Title -->
	<span class="flex-1 text-sm truncate {task.status === 'done' ? 'line-through text-text-dim' : 'text-text-primary'}">
		{task.title}
	</span>

	<!-- Recurrence badge -->
	{#if task.recurrence && task.recurrence !== 'none'}
		<span class="shrink-0 text-[10px] text-text-dim" title="Recurring: {task.recurrence}">🔄</span>
	{/if}

	<!-- Tags -->
	{#each (task.tags || []).slice(0, 2) as tag}
		<span class="shrink-0 px-1.5 py-0.5 text-[10px] rounded bg-bg-tertiary text-text-dim">{tag}</span>
	{/each}

	<!-- Due date -->
	{#if task.due_date}
		<span class="shrink-0 text-[11px] {isOverdue ? 'text-error font-medium' : 'text-text-dim'}">
			{formatDate(task.due_date)}
		</span>
	{/if}

	<!-- Delete (on hover) -->
	<button
		onclick={(e) => { e.stopPropagation(); deleteTask(task.id); }}
		class="shrink-0 opacity-0 group-hover:opacity-60 hover:!opacity-100 text-text-dim hover:text-error transition-all p-0.5"
		title="Delete"
	>
		<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
		</svg>
	</button>
</div>

<!-- Subtasks -->
{#each subtasks as sub (sub.id)}
	<svelte:self task={sub} subtasks={getChildSubtasks(sub.id)} depth={depth + 1} />
{/each}
