import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'
import { fileURLToPath } from 'node:url'
import { createVersionManifestPlugin } from '../scripts/vite-version-manifest.mjs'

const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig({
  base: process.env.VITE_BASE || '/admin/',
  plugins: [
    vue(),
    createVersionManifestPlugin({ repoRoot, application: 'admin' }),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  build: {
    // ECharts is loaded only when a chart page is opened; its isolated async chunk is intentionally below this ceiling.
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/node_modules/vue/') || id.includes('/node_modules/vue-router/') || id.includes('/node_modules/pinia/')) return 'vue-vendor'
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
