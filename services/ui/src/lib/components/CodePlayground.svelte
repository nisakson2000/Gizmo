<script lang="ts">
	import { codePlaygroundOpen, pendingAction } from '$lib/stores/settings';
	import { toast } from '$lib/stores/toast';
	import { marked } from 'marked';

	type Language = 'python' | 'javascript' | 'bash' | 'c' | 'cpp' | 'go' | 'lua' | 'html' | 'css' | 'svg' | 'markdown';

	const EXECUTABLE_LANGUAGES: Language[] = ['python', 'javascript', 'bash', 'c', 'cpp', 'go', 'lua'];
	const MARKUP_LANGUAGES: Language[] = ['html', 'css', 'svg', 'markdown'];
	const ALL_LANGUAGES: Language[] = [...EXECUTABLE_LANGUAGES, ...MARKUP_LANGUAGES];

	const LANGUAGE_LABELS: Record<Language, string> = {
		python: 'Python',
		javascript: 'JavaScript',
		bash: 'Bash',
		c: 'C',
		cpp: 'C++',
		go: 'Go',
		lua: 'Lua',
		html: 'HTML',
		css: 'CSS',
		svg: 'SVG',
		markdown: 'Markdown',
	};

	const PLACEHOLDERS: Record<Language, string> = {
		python: "# Python — numpy, pandas, matplotlib, sympy, scipy available\nprint('Hello, world!')",
		javascript: "// JavaScript (Node.js)\nconsole.log('Hello, world!');",
		bash: "#!/bin/bash\necho 'Hello, world!'",
		c: '#include <stdio.h>\n\nint main() {\n    printf("Hello, world!\\n");\n    return 0;\n}',
		cpp: '#include <iostream>\n\nint main() {\n    std::cout << "Hello, world!" << std::endl;\n    return 0;\n}',
		go: 'package main\n\nimport "fmt"\n\nfunc main() {\n    fmt.Println("Hello, world!")\n}',
		lua: "-- Lua\nprint('Hello, world!')",
		html: '<!DOCTYPE html>\n<html>\n<body>\n    <h1>Hello, world!</h1>\n</body>\n</html>',
		css: 'body {\n    background: #1a1a2e;\n    display: flex;\n    align-items: center;\n    justify-content: center;\n    height: 100vh;\n    margin: 0;\n}\n\nh1 {\n    color: #c8a0ff;\n    font-family: sans-serif;\n}',
		svg: '<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">\n    <circle cx="100" cy="100" r="80" fill="#d4a574" />\n    <text x="100" y="110" text-anchor="middle" fill="white" font-size="24">Gizmo</text>\n</svg>',
		markdown: '# Hello World\n\nThis is **bold** and this is *italic*.\n\n- Item 1\n- Item 2\n\n```python\nprint("code block")\n```',
	};

	let code = $state('');
	let running = $state(false);
	let output = $state<{ stdout: string; stderr: string; exit_code: number; timed_out: boolean } | null>(null);
	let error = $state('');
	let timeout = $state(10);
	let copied = $state(false);
	let language = $state<Language>('python');
	let previewHtml = $state('');

	let isMarkup = $derived(MARKUP_LANGUAGES.includes(language));

	// Reset state when modal opens
	$effect(() => {
		if ($codePlaygroundOpen) {
			code = '';
			output = null;
			error = '';
			running = false;
			copied = false;
			language = 'python';
			previewHtml = '';
		}
	});

	// Reset output/preview when language changes
	let lastLang = $state<Language>('python');
	$effect(() => {
		if (language !== lastLang) {
			lastLang = language;
			output = null;
			previewHtml = '';
			error = '';
			code = '';
		}
	});

	async function copyOutput() {
		if (!output) return;
		const text = [output.stdout, output.stderr].filter(Boolean).join('\n');
		await navigator.clipboard.writeText(text);
		copied = true;
		toast('Copied to clipboard', 'success');
		setTimeout(() => { copied = false; }, 1500);
	}

	function renderPreview() {
		if (language === 'html') {
			previewHtml = code;
		} else if (language === 'css') {
			previewHtml = `<!DOCTYPE html><html><head><style>${code}</style></head><body><h1>Heading</h1><p>Paragraph text for preview.</p><button>Button</button><div class="box" style="width:100px;height:100px;margin:16px 0;"></div><ul><li>Item 1</li><li>Item 2</li></ul></body></html>`;
		} else if (language === 'svg') {
			previewHtml = `<!DOCTYPE html><html><body style="margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:#111">${code}</body></html>`;
		} else if (language === 'markdown') {
			try {
				const parsed = marked.parse(code) as string;
				previewHtml = `<!DOCTYPE html><html><head><style>body{font-family:system-ui,sans-serif;padding:24px;max-width:720px;margin:0 auto;color:#e0e0e0;background:#0d0d0d;line-height:1.7}code{background:#1a1a1a;padding:2px 6px;border-radius:4px;font-size:0.9em}pre{background:#1a1a1a;padding:16px;border-radius:8px;overflow-x:auto}pre code{background:none;padding:0}h1,h2,h3{color:#fff}a{color:#d4a574}blockquote{border-left:3px solid #d4a574;margin-left:0;padding-left:16px;color:#aaa}</style></head><body>${parsed}</body></html>`;
			} catch {
				previewHtml = code;
			}
		}
	}

	async function runDirect() {
		if (!code.trim() || running) return;

		if (isMarkup) {
			renderPreview();
			return;
		}

		running = true;
		error = '';
		output = null;
		try {
			const resp = await fetch('/api/run-code', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ code: code.trim(), language, timeout }),
			});
			if (!resp.ok) {
				const err = await resp.json().catch(() => null);
				error = err?.error || `Execution failed (${resp.status})`;
				return;
			}
			output = await resp.json();
		} catch {
			error = 'Sandbox unavailable. Is the orchestrator running?';
		} finally {
			running = false;
		}
	}

	function askGizmo() {
		if (!code.trim()) return;
		const langLabel = LANGUAGE_LABELS[language];
		const prompt = `Run this ${langLabel} code and explain the output:\n\`\`\`${language}\n${code.trim()}\n\`\`\``;
		pendingAction.set({ type: 'prompt', text: prompt });
		codePlaygroundOpen.set(false);
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			codePlaygroundOpen.set(false);
		}
		if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
			e.preventDefault();
			runDirect();
		}
	}

	function handleTextareaKeydown(e: KeyboardEvent) {
		if (e.key === 'Tab') {
			e.preventDefault();
			const target = e.target as HTMLTextAreaElement;
			const start = target.selectionStart;
			const end = target.selectionEnd;
			code = code.substring(0, start) + '    ' + code.substring(end);
			requestAnimationFrame(() => {
				target.selectionStart = target.selectionEnd = start + 4;
			});
		}
		if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
			e.preventDefault();
			runDirect();
		}
	}

	function close() {
		codePlaygroundOpen.set(false);
	}
</script>

{#if $codePlaygroundOpen}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center"
		role="dialog"
		aria-label="Code Playground"
		onkeydown={handleKeydown}
	>
		<!-- svelte-ignore a11y_click_events_have_key_events -->
		<div class="absolute inset-0" onclick={close}></div>
		<div class="relative bg-bg-secondary border border-border/60 rounded-2xl w-full max-w-2xl mx-4 shadow-2xl max-h-[90vh] overflow-y-auto">
			<!-- Header -->
			<div class="flex items-center justify-between p-5 border-b border-border/40">
				<div class="flex items-center gap-2">
					<svg class="w-5 h-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
					</svg>
					<h2 class="text-base font-semibold">Code Playground</h2>
					<select
						bind:value={language}
						class="text-xs bg-bg-tertiary/50 text-text-secondary rounded-lg px-2 py-1 border border-border/30 outline-none cursor-pointer hover:border-border/50 transition-colors"
					>
						<optgroup label="Executable">
							{#each EXECUTABLE_LANGUAGES as lang}
								<option value={lang}>{LANGUAGE_LABELS[lang]}</option>
							{/each}
						</optgroup>
						<optgroup label="Preview">
							{#each MARKUP_LANGUAGES as lang}
								<option value={lang}>{LANGUAGE_LABELS[lang]}</option>
							{/each}
						</optgroup>
					</select>
				</div>
				<button
					onclick={close}
					class="text-text-dim hover:text-text-secondary transition-colors p-1"
					aria-label="Close"
				>
					<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M6 18L18 6M6 6l12 12" />
					</svg>
				</button>
			</div>

			<div class="p-5 space-y-4">
				<!-- Code Input -->
				<div>
					<textarea
						bind:value={code}
						onkeydown={handleTextareaKeydown}
						placeholder={PLACEHOLDERS[language]}
						rows="10"
						spellcheck="false"
						class="w-full resize-none bg-bg-primary border border-border/40 rounded-lg px-4 py-3 text-sm text-text-primary placeholder:text-text-dim focus:outline-none focus:border-accent/40 transition-colors font-mono leading-relaxed tab-size-4"
						style="tab-size: 4;"
					></textarea>
				</div>

				<!-- Timeout selector (executable only) -->
				{#if !isMarkup}
					<div class="flex items-center gap-3 text-xs text-text-dim">
						<span>Timeout:</span>
						<div class="flex gap-1">
							{#each [5, 10, 20, 30] as t}
								<button
									onclick={() => timeout = t}
									class="px-2 py-0.5 rounded-md font-medium transition-all {timeout === t
										? 'bg-accent text-white'
										: 'bg-bg-tertiary/50 text-text-dim border border-border/40 hover:border-border'}"
								>
									{t}s
								</button>
							{/each}
						</div>
						<span class="flex-1"></span>
						<span class="text-text-dim/60">Ctrl+Enter to run</span>
					</div>
				{/if}

				<!-- Error -->
				{#if error}
					<div class="px-3 py-2 bg-error/10 border border-error/20 rounded-lg text-xs text-error">{error}</div>
				{/if}

				<!-- Action Buttons -->
				<div class="flex gap-2">
					<button
						onclick={runDirect}
						disabled={!code.trim() || running}
						class="flex-1 py-2.5 rounded-xl text-sm font-medium transition-all {code.trim() && !running
							? 'bg-accent text-white hover:bg-accent-dim'
							: 'bg-bg-tertiary text-text-dim cursor-not-allowed'}"
					>
						{#if running}
							<span class="flex items-center justify-center gap-2">
								<svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
									<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
									<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
								</svg>
								Running...
							</span>
						{:else}
							{isMarkup ? 'Preview' : 'Run'}
						{/if}
					</button>
					{#if !isMarkup}
						<button
							onclick={askGizmo}
							disabled={!code.trim() || running}
							class="flex-1 py-2.5 rounded-xl text-sm font-medium transition-all {code.trim() && !running
								? 'bg-bg-tertiary text-text-secondary border border-border/50 hover:border-accent/40 hover:text-text-primary'
								: 'bg-bg-tertiary text-text-dim cursor-not-allowed'}"
						>
							Ask Gizmo
						</button>
					{/if}
				</div>

				<!-- Text Output (executable languages) -->
				{#if output && !isMarkup}
					<div class="bg-bg-primary border border-border/40 rounded-lg overflow-hidden">
						<div class="flex items-center justify-between px-3 py-1.5 border-b border-border/30 bg-bg-tertiary/30">
							<span class="text-xs text-text-dim font-medium">Output</span>
							<div class="flex items-center gap-2">
								{#if output.timed_out}
									<span class="text-xs text-amber-400">timed out</span>
								{/if}
								<span class="text-xs font-mono {output.exit_code === 0 ? 'text-success' : 'text-error'}">
									exit {output.exit_code}
								</span>
								<button
									onclick={copyOutput}
									class="p-0.5 rounded text-text-dim hover:text-text-primary transition-colors"
									aria-label="Copy output"
								>
									{#if copied}
										<svg class="w-3.5 h-3.5 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
											<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
										</svg>
									{:else}
										<svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
											<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15.666 3.888A2.25 2.25 0 0013.5 2.25h-3c-1.03 0-1.9.693-2.166 1.638m7.332 0c.055.194.084.4.084.612v0a.75.75 0 01-.75.75H9.75a.75.75 0 01-.75-.75v0c0-.212.03-.418.084-.612m7.332 0c.646.049 1.288.11 1.927.184 1.1.128 1.907 1.077 1.907 2.185V19.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 19.5V6.257c0-1.108.806-2.057 1.907-2.185a48.208 48.208 0 011.927-.184" />
										</svg>
									{/if}
								</button>
							</div>
						</div>
						<div class="px-4 py-3 max-h-64 overflow-y-auto">
							{#if output.stdout}
								<pre class="text-sm text-text-primary font-mono whitespace-pre-wrap leading-relaxed">{output.stdout}</pre>
							{/if}
							{#if output.stderr}
								<pre class="text-sm text-error/80 font-mono whitespace-pre-wrap leading-relaxed {output.stdout ? 'mt-2 pt-2 border-t border-border/30' : ''}">{output.stderr}</pre>
							{/if}
							{#if !output.stdout && !output.stderr}
								<p class="text-xs text-text-dim italic">No output</p>
							{/if}
						</div>
					</div>
				{/if}

				<!-- Visual Preview (markup languages) -->
				{#if previewHtml && isMarkup}
					<div class="bg-bg-primary border border-border/40 rounded-lg overflow-hidden">
						<div class="flex items-center justify-between px-3 py-1.5 border-b border-border/30 bg-bg-tertiary/30">
							<span class="text-xs text-text-dim font-medium">Preview</span>
						</div>
						<iframe
							srcdoc={previewHtml}
							sandbox="allow-scripts"
							class="w-full border-0 rounded-b-lg bg-white"
							style="height: 400px;"
							title="Markup preview"
						></iframe>
					</div>
				{/if}

				<p class="text-[11px] text-text-dim text-center">
					{isMarkup
						? 'Client-side rendering — no server execution'
						: 'Sandboxed execution — no network, 256MB RAM, read-only filesystem'}
				</p>
			</div>
		</div>
	</div>
{/if}
