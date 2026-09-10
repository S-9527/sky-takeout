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
          loadPaths: [fileURLToPath(new URL('./src/styles', import.meta.url))],
          additionalData:
            `@use 'variables' as *;\n` +
            `@use 'mixins' as *;\n`
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
        },
        '/files': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          secure: false
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false
    }
  }
})