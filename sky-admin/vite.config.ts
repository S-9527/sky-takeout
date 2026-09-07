import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    base: './',
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData:
            `@import "${fileURLToPath(new URL('./src/styles/_variables.scss', import.meta.url))}";\n` +
            `@import "${fileURLToPath(new URL('./src/styles/_mixins.scss', import.meta.url))}";\n`
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 8888,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          secure: false,
          ws: false,
          rewrite: (p) => p.replace(/^\/api/, '/admin')
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  }
})