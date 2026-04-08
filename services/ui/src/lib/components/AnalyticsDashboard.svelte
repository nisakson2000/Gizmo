<script lang="ts">
	import { onMount } from 'svelte';

	type Summary = {
		total_prompt_tokens: number;
		total_completion_tokens: number;
		total_tokens: number;
		total_messages: number;
		total_conversations: number;
		avg_response_ms: number;
		avg_context_ms: number;
		estimated_savings_usd: number;
		providers: Provider[];
	};

	type DailyRow = {
		date: string;
		prompt_tokens: number;
		completion_tokens: number;
		total_tokens: number;
		messages: number;
		avg_response_ms: number;
	};

	type Provider = {
		provider: string;
		input_price_per_1m: number;
		output_price_per_1m: number;
		estimated_cost_usd: number;
	};

	type ConvUsage = {
		conversation_id: string;
		title: string;
		prompt_tokens: number;
		completion_tokens: number;
		total_tokens: number;
		messages: number;
		last_active: string;
	};

	type ModeRow = {
		mode: string;
		total_tokens: number;
		messages: number;
	};

	let summary = $state<Summary | null>(null);
	let daily = $state<DailyRow[]>([]);
	let conversations = $state<ConvUsage[]>([]);
	let modes = $state<ModeRow[]>([]);
	let loading = $state(true);
	let daysRange = $state(30);

	function fmt(n: number): string {
		if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
		if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
		return n.toLocaleString();
	}

	function fmtMs(ms: number): string {
		if (ms >= 1000) return (ms / 1000).toFixed(1) + 's';
		return ms + 'ms';
	}

	async function loadAll() {
		loading = true;
		try {
			const [sRes, dRes, convRes, mRes] = await Promise.all([
				fetch('/api/analytics/summary'),
				fetch(`/api/analytics/daily?days=${daysRange}`),
				fetch('/api/analytics/conversations'),
				fetch('/api/analytics/modes'),
			]);
			summary = await sRes.json();
			daily = await dRes.json();
			conversations = await convRes.json();
			modes = await mRes.json();
		} catch (e) {
			console.error('Failed to load analytics:', e);
		}
		loading = false;
	}

	async function loadDaily() {
		try {
			daily = await (await fetch(`/api/analytics/daily?days=${daysRange}`)).json();
		} catch (e) {
			console.error('Failed to load daily:', e);
		}
	}

	let maxDailyTokens = $derived(Math.max(...daily.map(d => d.total_tokens), 1));
	let maxDailyTime = $derived(Math.max(...daily.map(d => d.avg_response_ms), 1));
	let maxConvTokens = $derived(conversations.length ? conversations[0].total_tokens : 1);
	let totalModeTokens = $derived(modes.reduce((a, m) => a + m.total_tokens, 0) || 1);
	let maxCost = $derived(summary?.providers?.[0]?.estimated_cost_usd || 1);

	onMount(loadAll);
</script>

<div class="console-frame flex flex-col h-full bg-bg-primary">
	<!-- Header -->
	<header class="flex items-center justify-between px-5 py-3 border-b border-border/60 bg-bg-secondary/50 shrink-0">
		<div class="flex items-center gap-3">
			<div class="w-9 h-9 rounded-xl bg-accent/15 flex items-center justify-center shadow-sm">
				<svg class="w-4.5 h-4.5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
				</svg>
			</div>
			<div class="flex flex-col">
				<span class="text-sm font-semibold text-text-primary tracking-tight">Analytics</span>
				{#if summary}
					<span class="text-[10px] text-text-dim/60">{fmt(summary.total_tokens)} tokens across {summary.total_conversations} conversations</span>
				{/if}
			</div>
		</div>

		<div class="flex items-center gap-2">
			<div class="flex bg-bg-tertiary/70 rounded-lg p-0.5 border border-border/20">
				{#each [7, 30, 90] as d}
					<button
						onclick={() => { daysRange = d; loadDaily(); }}
						class="px-3 py-1.5 text-xs font-semibold rounded-md transition-all
							{daysRange === d ? 'bg-bg-primary text-accent shadow-sm' : 'text-text-secondary hover:text-text-primary'}"
					>{d}d</button>
				{/each}
			</div>
		</div>
	</header>

	<!-- Dashboard content -->
	<div class="flex-1 overflow-y-auto p-4 sm:p-6 space-y-6">
		{#if loading}
			<div class="flex items-center justify-center h-40">
				<div class="w-6 h-6 border-2 border-accent/40 border-t-accent rounded-full animate-spin"></div>
			</div>
		{:else if !summary || summary.total_messages === 0}
			<div class="flex flex-col items-center justify-center h-60 text-text-dim">
				<svg class="w-12 h-12 mb-3 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
				</svg>
				<p class="text-sm">No analytics data yet</p>
				<p class="text-xs mt-1">Send some messages to start tracking usage</p>
			</div>
		{:else}
			<!-- Summary cards -->
			<div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
				<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
					<div class="text-[10px] uppercase tracking-wider text-text-dim mb-1">Total Tokens</div>
					<div class="text-xl font-bold text-text-primary">{fmt(summary.total_tokens)}</div>
					<div class="text-[10px] text-text-dim mt-1">{fmt(summary.total_prompt_tokens)} in / {fmt(summary.total_completion_tokens)} out</div>
				</div>
				<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
					<div class="text-[10px] uppercase tracking-wider text-text-dim mb-1">Messages</div>
					<div class="text-xl font-bold text-text-primary">{fmt(summary.total_messages)}</div>
					<div class="text-[10px] text-text-dim mt-1">{summary.total_conversations} conversations</div>
				</div>
				<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
					<div class="text-[10px] uppercase tracking-wider text-text-dim mb-1">Avg Response</div>
					<div class="text-xl font-bold text-text-primary">{fmtMs(summary.avg_response_ms)}</div>
					<div class="text-[10px] text-text-dim mt-1">+{fmtMs(summary.avg_context_ms)} context</div>
				</div>
				<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
					<div class="text-[10px] uppercase tracking-wider text-text-dim mb-1">Cloud Equivalent</div>
					<div class="text-xl font-bold text-green-400">${summary.estimated_savings_usd.toFixed(2)}</div>
					<div class="text-[10px] text-text-dim mt-1">vs Claude Opus 4</div>
				</div>
			</div>

			<!-- Daily usage chart -->
			{#if daily.length > 0}
				<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
					<h3 class="text-xs font-semibold text-text-primary mb-3">Daily Token Usage</h3>
					<div class="flex items-end gap-[2px] h-32">
						{#each daily as day}
							{@const promptPct = (day.prompt_tokens / maxDailyTokens) * 100}
							{@const compPct = (day.completion_tokens / maxDailyTokens) * 100}
							<div class="flex-1 flex flex-col justify-end h-full group relative min-w-0">
								<div class="flex flex-col justify-end flex-1">
									<div class="bg-accent/40 rounded-t-sm min-h-0" style="height: {compPct}%"></div>
									<div class="bg-accent rounded-t-sm min-h-0" style="height: {promptPct}%"></div>
								</div>
								<div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-1 bg-bg-tertiary rounded text-[9px] text-text-secondary whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-10">
									{day.date}<br>{fmt(day.total_tokens)} tokens / {day.messages} msgs
								</div>
							</div>
						{/each}
					</div>
					<div class="flex items-center gap-4 mt-2 text-[10px] text-text-dim">
						<div class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-accent inline-block"></span> Input</div>
						<div class="flex items-center gap-1"><span class="w-2 h-2 rounded-sm bg-accent/40 inline-block"></span> Output</div>
					</div>
				</div>
			{/if}

			<!-- Cost comparison + response time in two columns -->
			<div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
				<!-- Cost comparison -->
				{#if summary?.providers?.length}
					<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
						<h3 class="text-xs font-semibold text-text-primary mb-3">Cloud Cost Comparison</h3>
						<div class="text-[10px] text-text-dim mb-3">
							What your {fmt(summary.total_tokens)} tokens would cost on cloud providers
						</div>
						<div class="space-y-2">
							{#each summary.providers as p}
								{@const pct = maxCost > 0 ? (p.estimated_cost_usd / maxCost) * 100 : 0}
								<div>
									<div class="flex justify-between items-baseline mb-0.5">
										<span class="text-[11px] text-text-secondary">{p.provider}</span>
										<span class="text-[11px] font-semibold text-text-primary">${p.estimated_cost_usd.toFixed(2)}</span>
									</div>
									<div class="h-1.5 bg-bg-tertiary rounded-full overflow-hidden">
										<div class="h-full rounded-full transition-all duration-300"
											class:bg-red-400={pct > 80}
											class:bg-amber-400={pct > 30 && pct <= 80}
											class:bg-green-400={pct <= 30}
											style="width: {Math.max(pct, 2)}%"></div>
									</div>
								</div>
							{/each}
						</div>
						<div class="mt-3 pt-3 border-t border-border/20 flex items-baseline gap-2">
							<span class="text-[11px] text-text-dim">Your cost (local)</span>
							<span class="text-sm font-bold text-green-400">$0.00</span>
						</div>
					</div>
				{/if}

				<!-- Response time chart -->
				{#if daily.length > 0}
					<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
						<h3 class="text-xs font-semibold text-text-primary mb-3">Average Response Time</h3>
						<div class="flex items-end gap-[2px] h-32">
							{#each daily as day}
								{@const pct = (day.avg_response_ms / maxDailyTime) * 100}
								<div class="flex-1 flex flex-col justify-end h-full group relative min-w-0">
									<div class="bg-purple-400/70 rounded-t-sm" style="height: {pct}%"></div>
									<div class="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-1 bg-bg-tertiary rounded text-[9px] text-text-secondary whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity z-10">
										{day.date}<br>{fmtMs(day.avg_response_ms)}
									</div>
								</div>
							{/each}
						</div>
					</div>
				{/if}
			</div>

			<!-- Top conversations + mode breakdown in two columns -->
			<div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
				<!-- Top conversations -->
				{#if conversations.length > 0}
					<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
						<h3 class="text-xs font-semibold text-text-primary mb-3">Top Conversations by Usage</h3>
						<div class="space-y-2 max-h-64 overflow-y-auto">
							{#each conversations as conv, i}
								{@const pct = (conv.total_tokens / maxConvTokens) * 100}
								<div>
									<div class="flex justify-between items-baseline mb-0.5">
										<span class="text-[11px] text-text-secondary truncate max-w-[60%]" title={conv.title}>
											{conv.title || 'Untitled'}
										</span>
										<span class="text-[10px] text-text-dim shrink-0">{fmt(conv.total_tokens)} tokens</span>
									</div>
									<div class="h-1.5 bg-bg-tertiary rounded-full overflow-hidden">
										<div class="h-full bg-accent rounded-full" style="width: {pct}%"></div>
									</div>
								</div>
							{/each}
						</div>
					</div>
				{/if}

				<!-- Mode breakdown -->
				{#if modes.length > 0}
					<div class="bg-bg-secondary/60 rounded-xl border border-border/30 p-4">
						<h3 class="text-xs font-semibold text-text-primary mb-3">Usage by Mode</h3>
						<div class="space-y-2">
							{#each modes as m}
								{@const pct = (m.total_tokens / totalModeTokens) * 100}
								<div>
									<div class="flex justify-between items-baseline mb-0.5">
										<span class="text-[11px] text-text-secondary capitalize">{m.mode}</span>
										<span class="text-[10px] text-text-dim">{fmt(m.total_tokens)} tokens ({m.messages} msgs)</span>
									</div>
									<div class="h-1.5 bg-bg-tertiary rounded-full overflow-hidden">
										<div class="h-full bg-accent/70 rounded-full" style="width: {pct}%"></div>
									</div>
								</div>
							{/each}
						</div>
					</div>
				{/if}
			</div>
		{/if}
	</div>
</div>
