<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import {
		activeTab,
		tasks,
		notes,
		selectedTaskId,
		selectedNoteId,
		trackerChatOpen,
		loadTasks,
		loadNotes,
		loadTags,
	} from '$lib/stores/tracker';
	import { connectTracker, disconnectTracker } from '$lib/ws/tracker-client';

	import QuickAdd from '$lib/components/tracker/QuickAdd.svelte';
	import TagFilter from '$lib/components/tracker/TagFilter.svelte';
	import TaskList from '$lib/components/tracker/TaskList.svelte';
	import NoteList from '$lib/components/tracker/NoteList.svelte';
	import TaskDetail from '$lib/components/tracker/TaskDetail.svelte';
	import NoteEditor from '$lib/components/tracker/NoteEditor.svelte';
	import TrackerChat from '$lib/components/tracker/TrackerChat.svelte';

	let taskCount = $derived($tasks.filter(t => t.status !== 'done').length);
	let noteCount = $derived($notes.length);

	onMount(() => {
		loadTasks();
		loadNotes();
		loadTags();
		connectTracker();
	});

	onDestroy(() => {
		disconnectTracker();
	});
</script>

<svelte:head>
	<title>Gizmo-AI — Tracker</title>
</svelte:head>

<div class="console-frame flex flex-col h-full bg-bg-primary">
	<!-- Header bar -->
	<header class="flex items-center justify-between px-5 h-12 border-b border-border/60 bg-bg-primary shrink-0">
		<div class="flex items-center gap-3">
			<svg class="w-5 h-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
			</svg>
			<span class="text-sm font-semibold text-text-primary tracking-tight">Tracker</span>
		</div>

		<div class="flex items-center gap-2">
			<!-- Tab toggle -->
			<div class="flex bg-bg-tertiary rounded-lg p-0.5">
				<button
					onclick={() => activeTab.set('tasks')}
					class="px-3 py-1 text-xs font-medium rounded-md transition-all
						{$activeTab === 'tasks'
							? 'bg-bg-primary text-accent shadow-sm'
							: 'text-text-secondary hover:text-text-primary'}"
				>
					Tasks <span class="text-text-dim ml-0.5">{taskCount}</span>
				</button>
				<button
					onclick={() => activeTab.set('notes')}
					class="px-3 py-1 text-xs font-medium rounded-md transition-all
						{$activeTab === 'notes'
							? 'bg-bg-primary text-accent shadow-sm'
							: 'text-text-secondary hover:text-text-primary'}"
				>
					Notes <span class="text-text-dim ml-0.5">{noteCount}</span>
				</button>
			</div>

			<!-- Chat toggle -->
			<button
				onclick={() => trackerChatOpen.update(v => !v)}
				class="flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-md transition-colors
					{$trackerChatOpen ? 'bg-accent/15 text-accent' : 'text-text-dim hover:text-text-secondary hover:bg-bg-hover'}"
			>
				<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
				</svg>
				Gizmo
			</button>
		</div>
	</header>

	<!-- Quick add -->
	<QuickAdd />

	<!-- Main content area -->
	<div class="console-screen flex flex-1 overflow-hidden">
		<!-- Tag sidebar (tasks tab only) -->
		{#if $activeTab === 'tasks'}
			<TagFilter />
		{/if}

		<!-- Center: list -->
		<div class="flex-1 flex flex-col overflow-hidden">
			<div class="flex-1 overflow-y-auto">
				{#if $activeTab === 'tasks'}
					<TaskList />
				{:else}
					<NoteList />
				{/if}
			</div>
		</div>

		<!-- Detail panel -->
		{#if $activeTab === 'tasks' && $selectedTaskId}
			<TaskDetail />
		{/if}
		{#if $activeTab === 'notes' && $selectedNoteId}
			<NoteEditor />
		{/if}

		<!-- Tracker chat panel -->
		{#if $trackerChatOpen}
			<TrackerChat />
		{/if}
	</div>
</div>
