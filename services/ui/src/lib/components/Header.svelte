<script lang="ts">
	import { connectionStatus } from '$lib/stores/connection';
	import { thinkingEnabled, ttsEnabled, sidebarOpen } from '$lib/stores/settings';

	let statusColor = $derived(
		$connectionStatus === 'connected'
			? 'bg-success'
			: $connectionStatus === 'generating'
				? 'bg-accent animate-pulse'
				: $connectionStatus === 'connecting'
					? 'bg-accent animate-pulse'
					: 'bg-error'
	);

	let statusText = $derived(
		$connectionStatus === 'generating'
			? 'Generating'
			: $connectionStatus === 'connecting'
				? 'Connecting'
				: $connectionStatus === 'connected'
					? 'Online'
					: 'Offline'
	);
</script>

<header class="flex items-center justify-between px-4 h-12 border-b border-border/60 bg-bg-primary">
	<div class="flex items-center gap-3">
		<button
			onclick={() => sidebarOpen.update((v) => !v)}
			class="text-text-dim hover:text-text-secondary p-1 -ml-1 transition-colors"
			aria-label="Toggle sidebar"
		>
			<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
			</svg>
		</button>
		<span class="text-sm font-semibold text-text-primary tracking-tight">Gizmo</span>
	</div>

	<div class="flex items-center gap-1.5">
		<button
			onclick={() => thinkingEnabled.update((v) => !v)}
			class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium transition-all {$thinkingEnabled
				? 'bg-accent/15 text-accent border border-accent/30'
				: 'text-text-dim hover:text-text-secondary hover:bg-bg-hover'}"
			aria-label="Toggle thinking mode"
		>
			<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
			</svg>
			Think
		</button>

		<button
			onclick={() => ttsEnabled.update((v) => !v)}
			class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium transition-all {$ttsEnabled
				? 'bg-accent/15 text-accent border border-accent/30'
				: 'text-text-dim hover:text-text-secondary hover:bg-bg-hover'}"
			aria-label="Toggle text-to-speech"
		>
			<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
			</svg>
			TTS
		</button>

		<div class="flex items-center gap-1.5 ml-1.5 pl-2 border-l border-border/40">
			<div class="w-1.5 h-1.5 rounded-full {statusColor}"></div>
			<span class="text-[11px] text-text-dim">{statusText}</span>
		</div>
	</div>
</header>
