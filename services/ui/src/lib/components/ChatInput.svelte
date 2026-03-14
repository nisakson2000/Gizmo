<script lang="ts">
	import { generating, addUserMessage } from '$lib/stores/chat';
	import { send, stopGeneration } from '$lib/ws/client';
	import { connectionStatus } from '$lib/stores/connection';

	let input = $state('');
	let uploadError = $state('');
	let textarea: HTMLTextAreaElement;

	const MAX_DOC_SIZE = 10 * 1024 * 1024;
	const MAX_IMAGE_SIZE = 20 * 1024 * 1024;

	function handleSubmit() {
		const text = input.trim();
		if (!text) return;
		if ($generating) {
			showError('Still generating — wait or click stop.');
			return;
		}
		if ($connectionStatus === 'disconnected' || $connectionStatus === 'connecting') {
			showError(`Can't send — ${$connectionStatus}. Waiting for WebSocket...`);
			return;
		}
		addUserMessage(text);
		send(text);
		input = '';
		if (textarea) textarea.style.height = 'auto';
	}

	function autoResize() {
		if (textarea) {
			textarea.style.height = 'auto';
			textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px';
		}
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter' && !e.shiftKey) {
			e.preventDefault();
			handleSubmit();
		}
	}

	function showError(msg: string) {
		uploadError = msg;
		setTimeout(() => (uploadError = ''), 5000);
	}

	function handleFileUpload() {
		const inp = document.createElement('input');
		inp.type = 'file';
		inp.accept = 'image/*,.pdf,.txt,.md,.py,.js,.ts,.json,.yaml,.yml,.toml,.csv';
		inp.onchange = async (e) => {
			const file = (e.target as HTMLInputElement).files?.[0];
			if (!file) return;

			const isImage = file.type.startsWith('image/');
			const maxSize = isImage ? MAX_IMAGE_SIZE : MAX_DOC_SIZE;
			if (file.size > maxSize) {
				showError(`File too large. Max ${isImage ? '20MB' : '10MB'}.`);
				return;
			}

			const formData = new FormData();
			formData.append('file', file);
			const endpoint = isImage ? '/api/upload-image' : '/api/upload';

			try {
				const resp = await fetch(endpoint, { method: 'POST', body: formData });
				if (!resp.ok) {
					showError('Upload failed. Server returned an error.');
					return;
				}
				const data = await resp.json();
				if (isImage) {
					addUserMessage(`[Uploaded image: ${data.filename}]`);
					send(`[Image uploaded: ${data.filename}] Please analyze this image.`);
				} else {
					const preview = data.content?.substring(0, 500) || '';
					addUserMessage(`[Uploaded file: ${data.filename}]\n\`\`\`\n${preview}\n\`\`\``);
					send(`I've uploaded a file called "${data.filename}" with this content:\n\n${data.content}`);
				}
			} catch {
				showError('Upload failed. Check your connection.');
			}
		};
		inp.click();
	}
</script>

<div class="border-t border-border/40 bg-bg-primary px-4 pb-4 pt-3">
	{#if uploadError}
		<div class="max-w-3xl mx-auto mb-2 px-3 py-1.5 bg-error/10 border border-error/20 rounded-lg text-xs text-error">
			{uploadError}
		</div>
	{/if}
	<div class="max-w-3xl mx-auto">
		<div class="flex items-end gap-2 bg-bg-secondary border border-border/60 rounded-2xl px-3 py-2 focus-within:border-accent/40 transition-colors">
			<button
				onclick={handleFileUpload}
				class="p-1.5 text-text-dim hover:text-text-secondary transition-colors flex-shrink-0 mb-0.5"
				aria-label="Upload file"
				disabled={$generating}
			>
				<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13" />
				</svg>
			</button>

			<textarea
				bind:this={textarea}
				bind:value={input}
				onkeydown={handleKeydown}
				oninput={autoResize}
				placeholder={$connectionStatus === 'connected' || $connectionStatus === 'generating' ? 'Message Gizmo...' : 'Connecting...'}
				disabled={$connectionStatus === 'disconnected'}
				rows="1"
				class="flex-1 resize-none bg-transparent text-text-primary placeholder:text-text-dim focus:outline-none text-[15px] leading-[1.5] max-h-[200px] py-1"
			></textarea>

			{#if $generating}
				<button
					onclick={() => stopGeneration()}
					class="p-1.5 rounded-lg bg-text-dim/20 text-text-secondary hover:bg-text-dim/30 transition-colors flex-shrink-0 mb-0.5"
					aria-label="Stop generation"
				>
					<svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
						<rect x="7" y="7" width="10" height="10" rx="1.5" />
					</svg>
				</button>
			{:else}
				<button
					onclick={handleSubmit}
					disabled={!input.trim()}
					class="p-1.5 rounded-lg flex-shrink-0 mb-0.5 transition-all {input.trim()
						? 'bg-accent text-white hover:bg-accent-dim'
						: 'bg-transparent text-text-dim'}"
					aria-label="Send message"
				>
					<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.5 10.5L12 3m0 0l7.5 7.5M12 3v18" />
					</svg>
				</button>
			{/if}
		</div>
		<p class="text-[11px] text-text-dim text-center mt-1.5">Gizmo runs entirely on your machine. Responses may be inaccurate.</p>
	</div>
</div>
