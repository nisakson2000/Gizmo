<script lang="ts">
	import { connectionStatus } from '$lib/stores/connection';
	import { sidebarOpen } from '$lib/stores/settings';

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
		<div class="w-1.5 h-1.5 rounded-full {statusColor}"></div>
		<span class="text-[11px] text-text-dim">{statusText}</span>
	</div>
</header>
