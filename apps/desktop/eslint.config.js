import js from '@eslint/js'
import ts from '@typescript-eslint/eslint-plugin'
import tsParser from '@typescript-eslint/parser'
import svelte from 'eslint-plugin-svelte'
import svelteParser from 'svelte-eslint-parser'
import globals from 'globals'

// Base TS rules without type-checking (for config files)
const tsBaseRules = {
	'@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
	'@typescript-eslint/no-explicit-any': 'warn',
	'@typescript-eslint/no-non-null-assertion': 'warn',
	'no-console': ['warn', { allow: ['warn', 'error'] }],
}

export default [
	js.configs.recommended,
	...svelte.configs['flat/recommended'],

	// Config/tool files — basic TS only, no type-aware rules
	{
		files: ['*.ts', '*.js', 'vite.config.*', 'svelte.config.*', 'vitest.config.*'],
		plugins: { '@typescript-eslint': ts },
		languageOptions: {
			parser: tsParser,
			globals: { ...globals.node },
		},
		rules: {
			...ts.configs['recommended'].rules,
			...tsBaseRules,
		},
	},

	// Source TypeScript files — full type-aware linting
	{
		files: ['src/**/*.ts', 'src/**/*.js'],
		plugins: { '@typescript-eslint': ts },
		languageOptions: {
			parser: tsParser,
			parserOptions: { project: './tsconfig.json', extraFileExtensions: ['.svelte'] },
			globals: { ...globals.browser, ...globals.node },
		},
		rules: {
			...ts.configs['recommended'].rules,
			...tsBaseRules,
		},
	},

	// Svelte files — type-aware
	{
		files: ['src/**/*.svelte'],
		plugins: { '@typescript-eslint': ts, svelte },
		languageOptions: {
			parser: svelteParser,
			parserOptions: { parser: tsParser, project: './tsconfig.json', extraFileExtensions: ['.svelte'] },
			globals: { ...globals.browser },
		},
		rules: {
			...ts.configs['recommended'].rules,
			...tsBaseRules,
			'svelte/no-unused-svelte-ignore': 'error',
			'svelte/valid-compile': 'error',
			'svelte/no-at-html-tags': 'warn', // warn not error — intentional {@html} in stories/proposals
		},
	},

	// Ignored paths
	{
		ignores: [
			'build/',
			'.svelte-kit/',
			'node_modules/',
			'src-tauri/',
			'gen/',
			'apps/chrome-ext/',
			'static/',
		],
	},
]
