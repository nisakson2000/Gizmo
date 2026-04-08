<script lang="ts">
	import { onDestroy } from 'svelte';
	import { streamingAudioChunks, streamingAudioDone } from '$lib/stores/chat';

	let audioCtx: AudioContext | null = $state(null);
	let nextPlayTime = $state(0);
	let isPlaying = $state(false);
	let processedCount = $state(0);
	let totalChunks = $state(0);
	let done = $state(false);

	// Buffer chunks by sentence order, then chunk order within sentence
	let pendingChunks: Map<string, { buffer: AudioBuffer; sentenceIndex: number; chunkIndex: number }> = new Map();
	let nextSentence = 0;
	let nextChunkInSentence: Map<number, number> = new Map(); // tracks next expected chunk per sentence

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
		// Schedule chunks in sentence order, then chunk order within each sentence
		while (true) {
			const nextChunk = nextChunkInSentence.get(nextSentence) ?? 0;
			const key = `${nextSentence}:${nextChunk}`;
			const entry = pendingChunks.get(key);
			if (!entry) {
				// Check if this sentence is complete and we should move to the next
				// A sentence is "done" if we've received chunks for a later sentence
				// and this sentence has no more pending chunks
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

	// Process incoming chunks
	const unsub = streamingAudioChunks.subscribe((chunks) => {
		if (chunks.length <= totalChunks) return;

		const ctx = getOrCreateContext();

		for (let i = totalChunks; i < chunks.length; i++) {
			const { meta, blob } = chunks[i];

			blob.arrayBuffer().then((arrayBuf) => {
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
			});
		}

		totalChunks = chunks.length;
	});

	const unsubDone = streamingAudioDone.subscribe((isDone) => {
		done = isDone;
	});

	onDestroy(() => {
		unsub();
		unsubDone();
		if (audioCtx && audioCtx.state !== 'closed') {
			audioCtx.close();
		}
	});
</script>

{#if isPlaying || totalChunks > 0}
	<div class="flex items-center gap-2 mt-3 px-3 py-2 bg-bg-tertiary/50 rounded-lg">
		{#if !done}
			<!-- Pulsing indicator during streaming -->
			<div class="flex items-center gap-1.5">
				<div class="w-2 h-2 bg-accent rounded-full animate-pulse"></div>
				<span class="text-xs text-text-dim">Streaming audio...</span>
			</div>
		{:else}
			<!-- Done indicator -->
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
