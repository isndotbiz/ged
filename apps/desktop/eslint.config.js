import js from '@eslint/js'
import ts from '@typescript-eslint/eslint-plugin'
import tsParser from '@typescript-eslint/parser'
import svelte from 'eslint-plugin-svelte'
import svelteParser from 'svelte-eslint-parser'
import globals from 'globals'

export default [
	js.configs.recommended,
	...svelte.configs['flat/recommended'],

	// TypeScript files
	{
		files: ['**/*.ts', '**/*.js'],
		plugins: { '@typescript-eslint': ts },
		languageOptions: {
			parser: tsParser,
			parserOptions: { project: './tsconfig.json', extraFileExtensions: ['.svelte'] },
			globals: { ...globals.browser, ...globals.node },
		},
		rules: {
			...ts.configs['recommended'].rules,
			'@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
			'@typescript-eslint/no-explicit-any': 'warn',
			'@typescript-eslint/no-non-null-assertion': 'warn',
			'no-console': ['warn', { allow: ['warn', 'error'] }],
		},
	},

	// Svelte files
	{
		files: ['**/*.svelte'],
		plugins: { '@typescript-eslint': ts, svelte },
		languageOptions: {
			parser: svelteParser,
			parserOptions: { parser: tsParser, extraFileExtensions: ['.svelte'] },
			globals: { ...globals.browser },
		},
		rules: {
			...ts.configs['recommended'].rules,
			'@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
			'@typescript-eslint/no-explicit-any': 'warn',
			'svelte/no-unused-svelte-ignore': 'error',
			'svelte/valid-compile': 'error',
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
			'vite.config.js',
			'svelte.config.js',
		],
	},
]
