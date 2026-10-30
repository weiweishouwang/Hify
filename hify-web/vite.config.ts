import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/** package.json 里的版本号，注入给前端显示（侧边栏底部）。 */
const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as {
  version: string
}

// Hify 前端构建配置
//
// 开发期：vite dev server 跑在 5173，/api 走代理转发到后端 8080，
//         浏览器看到的仍是同源 /api，因此前端代码里不出现绝对地址。
// 生产期：产物是纯静态文件，nginx 反代 /api 到 api:8080，同源。
// 两种环境用同一套 API_BASE_URL，不需要区分环境写代码。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],

    // 版本号来自 package.json，不在前端代码里写死
    define: {
      __APP_VERSION__: JSON.stringify(pkg.version),
    },

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // 不要设置 timeout / proxyTimeout：
          // 那两个是套接字空闲超时，SSE 空闲 30 秒就会被掐断，
          // 而后端本来就靠 15–30s 心跳保活。超时交给后端自己的四层超时管。
          configure(proxy) {
            proxy.on('proxyRes', (proxyRes) => {
              const contentType = String(proxyRes.headers['content-type'] || '')
              if (contentType.includes('text/event-stream')) {
                // 标记为不可缓冲，防止任何一层中间件攒完整个响应再下发
                proxyRes.headers['x-accel-buffering'] = 'no'
              }
            })
          },
        },
      },
    },

    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // Vite 8 底层是 Rolldown，没有 Rollup 的 manualChunks 对象写法，
          // 手动分包走 codeSplitting.groups。
          codeSplitting: {
            groups: [
              { name: 'vendor', test: /node_modules[\\/](vue|vue-router|pinia)[\\/]/ },
              { name: 'element', test: /node_modules[\\/](element-plus|@element-plus)[\\/]/ },
            ],
          },
        },
      },
    },

    test: {
      environment: 'node',
      include: ['src/**/*.test.ts'],
    },
  }
})
