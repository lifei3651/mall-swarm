import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { fileURLToPath } from 'node:url'
import { createVersionManifestPlugin } from '../scripts/vite-version-manifest.mjs'

const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig(({ mode }) => {
  const surface = mode === 'team' ? 'team' : mode === 'integrated' ? 'integrated' : 'public'
  const teamSurface = surface === 'team'
  const integratedSurface = surface === 'integrated'
  const application = teamSurface ? 'team-h5' : integratedSurface ? 'integrated-h5' : 'storefront-public'
  return {
  plugins: [vue(), createVersionManifestPlugin({ repoRoot, application })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@surface-app': path.resolve(__dirname, teamSurface ? 'src/surfaces/team/TeamApp.vue' : 'src/App.vue'),
      '@surface-router': path.resolve(__dirname, teamSurface
        ? 'src/surfaces/team/router.js'
        : integratedSurface
          ? 'src/surfaces/integrated/router.js'
          : 'src/router/index.js'),
      '@surface-commerce-policy': path.resolve(__dirname, integratedSurface
        ? 'src/surfaces/integrated/commercePolicy.js'
        : 'src/surfaces/public/commercePolicy.js'),
    },
  },
  server: {
    port: teamSurface ? 3002 : integratedSurface ? 3003 : 3001,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_PROXY || 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (url) => url.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    outDir: teamSurface ? 'dist-team' : integratedSurface ? 'dist-integrated' : 'dist',
  },
  }
})
