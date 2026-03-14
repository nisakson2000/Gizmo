<script lang="ts">
	import { thinkingEnabled, ttsEnabled, contextLength, settingsOpen } from '$lib/stores/settings';

	interface ServiceHealth {
		[key: string]: { status: string; error?: string };
	}
	let health = $state<ServiceHealth>({});

	async function checkHealth() {
		try {
			const resp = await fetch('/api/services/health');
			if (resp.ok) health = await resp.json();
		} catch {
			health = {};
		}
	}

	$effect(() => {
		if ($settingsOpen) checkHealth();
	});

	let serviceCount = $derived(Object.keys(health).length);
</script>

{#if $settingsOpen}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center"
		role="dialog"
		aria-label="Settings"
		onkeydown={(e) => { if (e.key === 'Escape') settingsOpen.set(false); }}
	>
		<!-- svelte-ignore a11y_click_events_have_key_events -->
		<div class="absolute inset-0" onclick={() => settingsOpen.set(false)}></div>
		<div class="relative bg-bg-secondary border border-border/60 rounded-2xl w-full max-w-lg mx-4 max-h-[80vh] overflow-y-auto shadow-2xl">
			<div class="flex items-center justify-between p-5 border-b border-border/40">
				<h2 class="text-base font-semibold">Settings</h2>
				<button
					onclick={() => settingsOpen.set(false)}
					class="text-text-dim hover:text-text-secondary transition-colors p-1"
					aria-label="Close settings"
				>
					<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M6 18L18 6M6 6l12 12" />
					</svg>
				</button>
			</div>

			<div class="p-5 space-y-6">
				<!-- Thinking Mode -->
				<div class="flex items-center justify-between">
					<div class="flex-1 mr-4">
						<p class="text-sm font-medium">Thinking Mode</p>
						<p class="text-xs text-text-dim mt-0.5">Step-by-step reasoning before responding. Better for complex problems.</p>
					</div>
					<button
						onclick={() => thinkingEnabled.update((v) => !v)}
						class="w-10 h-[22px] rounded-full transition-colors flex-shrink-0 {$thinkingEnabled ? 'bg-accent' : 'bg-bg-tertiary border border-border/60'}"
						aria-label="Toggle thinking mode"
					>
						<div class="w-4 h-4 rounded-full bg-white shadow-sm transition-transform {$thinkingEnabled ? 'translate-x-[22px]' : 'translate-x-[3px]'}"></div>
					</button>
				</div>

				<!-- TTS -->
				<div class="flex items-center justify-between">
					<div class="flex-1 mr-4">
						<p class="text-sm font-medium">Text-to-Speech</p>
						<p class="text-xs text-text-dim mt-0.5">Speak responses via Qwen3-TTS. GPU-accelerated, auto-unloads when idle.</p>
					</div>
					<button
						onclick={() => ttsEnabled.update((v) => !v)}
						class="w-10 h-[22px] rounded-full transition-colors flex-shrink-0 {$ttsEnabled ? 'bg-accent' : 'bg-bg-tertiary border border-border/60'}"
						aria-label="Toggle text-to-speech"
					>
						<div class="w-4 h-4 rounded-full bg-white shadow-sm transition-transform {$ttsEnabled ? 'translate-x-[22px]' : 'translate-x-[3px]'}"></div>
					</button>
				</div>

				<!-- Context Length -->
				<div>
					<div class="flex items-center justify-between mb-1">
						<p class="text-sm font-medium">Context Length</p>
						<span class="text-xs text-text-dim font-mono">{$contextLength.toLocaleString()} tokens</span>
					</div>
					<p class="text-xs text-text-dim mb-2">How much conversation history the model sees.</p>
					<input
						id="context-length"
						type="range"
						min="2048"
						max="32768"
						step="1024"
						bind:value={$contextLength}
						class="w-full accent-accent"
					/>
					<div class="flex justify-between text-[11px] text-text-dim mt-0.5">
						<span>2K</span>
						<span>32K</span>
					</div>
				</div>

				<!-- Service Health -->
				<div>
					<div class="flex items-center justify-between mb-2">
						<p class="text-sm font-medium">Services <span class="text-text-dim font-normal">({serviceCount})</span></p>
						<button onclick={checkHealth} class="text-xs text-accent hover:text-accent-dim transition-colors">Refresh</button>
					</div>
					<div class="space-y-1">
						{#each Object.entries(health) as [name, info]}
							<div class="flex items-center justify-between bg-bg-tertiary/50 rounded-lg px-3 py-2">
								<span class="text-sm text-text-secondary">{name}</span>
								<div class="flex items-center gap-1.5">
									<div class="w-1.5 h-1.5 rounded-full {info.status === 'ok' ? 'bg-success' : 'bg-error'}"></div>
									<span class="text-xs text-text-dim font-mono">{info.status}</span>
								</div>
							</div>
						{/each}
					</div>
				</div>
			</div>
		</div>
	</div>
{/if}
