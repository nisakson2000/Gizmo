<script lang="ts">
	import { onDestroy } from 'svelte';
	import { streamingAudioChunks, streamingAudioDone } from '$lib/stores/chat';

	let audioCtx: AudioContext | null = $state(null);
	let nextPlayTime = $state(0);
	let isPlaying = $state(false);
	let processedCount = $state(0);
	let lastProcessedIndex = $state(0);
	let destroyed = false;

	// Buffer chunks by sentence order, then chunk order within sentence
	let pendingChunks: Map<string, { buffer: AudioBuffer; sentenceIndex: number; chunkIndex: number }> = new Map();
	let nextSentence = 0;
	let nextChunkInSentence: Map<number, number> = new Map();

	function getOrCreateContext(): AudioContext {
		if (!audioCtx) {
			audioCtx = new AudioContext({ sampleRate: 24000 });
		}
		if (audioCtx.state === 'suspended') {
			audioCtx.resume();
		}
		return audioCtx;
	}

	function scheduleBuffer(buffer: AudioBuffer) {
		const ctx = getOrCreateContext();
		const source = ctx.createBufferSource();
		source.buffer = buffer;
		source.connect(ctx.destination);

		const now = ctx.currentTime;
		if (nextPlayTime < now) {
			nextPlayTime = now + 0.02; // 20ms cushion if chunk arrived late
		}

		source.start(nextPlayTime);
		nextPlayTime += buffer.duration;
		isPlaying = true;
		processedCount++;
	}

	function tryScheduleNext() {
		while (true) {
			const nextChunk = nextChunkInSentence.get(nextSentence) ?? 0;
			const key = `${nextSentence}:${nextChunk}`;
			const entry = pendingChunks.get(key);
			if (!entry) {
				if (nextChunk > 0) {
					const nextSentKey = `${nextSentence + 1}:0`;
					if (pendingChunks.has(nextSentKey)) {
						nextSentence++;
						continue;
					}
				}
				break;
			}
			pendingChunks.delete(key);
			scheduleBuffer(entry.buffer);
			nextChunkInSentence.set(nextSentence, nextChunk + 1);
		}
	}

	const unsub = streamingAudioChunks.subscribe((chunks) => {
		if (destroyed || chunks.length <= lastProcessedIndex) return;

		const ctx = getOrCreateContext();

		for (let i = lastProcessedIndex; i < chunks.length; i++) {
			const { meta, blob } = chunks[i];

			blob.arrayBuffer().then((arrayBuf) => {
				if (destroyed) return;
				const float32 = new Float32Array(arrayBuf);
				const audioBuffer = ctx.createBuffer(1, float32.length, meta.sampleRate);
				audioBuffer.getChannelData(0).set(float32);

				const key = `${meta.sentenceIndex}:${meta.chunkIndex}`;
				pendingChunks.set(key, {
					buffer: audioBuffer,
					sentenceIndex: meta.sentenceIndex,
					chunkIndex: meta.chunkIndex,
				});

				tryScheduleNext();
			}).catch(() => {});
		}

		lastProcessedIndex = chunks.length;
	});

	onDestroy(() => {
		destroyed = true;
		unsub();
		pendingChunks.clear();
		nextChunkInSentence.clear();
		if (audioCtx && audioCtx.state !== 'closed') {
			audioCtx.close();
		}
	});
</script>

{#if isPlaying || lastProcessedIndex > 0}
	<div class="flex items-center gap-2 mt-3 px-3 py-2 bg-bg-tertiary/50 rounded-lg">
		{#if !$streamingAudioDone}
			<div class="flex items-center gap-1.5">
				<div class="w-2 h-2 bg-accent rounded-full animate-pulse"></div>
				<span class="text-xs text-text-dim">Streaming audio...</span>
			</div>
		{:else}
			<div class="flex items-center gap-1.5">
				<svg class="w-3.5 h-3.5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
				</svg>
				<span class="text-xs text-text-dim">Audio complete</span>
			</div>
		{/if}
		<span class="text-[10px] text-text-dim/60 ml-auto font-mono">
			{processedCount} chunks
		</span>
	</div>
{/if}
