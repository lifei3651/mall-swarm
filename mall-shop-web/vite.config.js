import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'node:url'
import { createVersionManifestPlugin } from '../scripts/vite-version-manifest.mjs'

const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig(({ mode }) => {
  const teamSurface = mode === 'team'
  return {
  plugins: [vue(), createVersionManifestPlugin({ repoRoot, application: teamSurface ? 'team-h5' : 'storefront-public' })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@surface-app': path.resolve(__dirname, teamSurface ? 'src/surfaces/team/TeamApp.vue' : 'src/App.vue'),
      '@surface-router': path.resolve(__dirname, teamSurface ? 'src/surfaces/team/router.js' : 'src/router/index.js'),
    },
  },
  server: {
    port: teamSurface ? 3002 : 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (url) => url.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    outDir: teamSurface ? 'dist-team' : 'dist',
  },
  }
})
