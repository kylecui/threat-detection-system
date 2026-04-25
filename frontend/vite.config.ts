/// <reference types="vitest/config" />
import { defineConfig, type UserConfig as ViteUserConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { fileURLToPath } from 'url';
import type { InlineConfig as VitestInlineConfig } from 'vitest/node';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
type TestableViteConfig = ViteUserConfig & { test?: VitestInlineConfig };

// https://vitejs.dev/config/
const config: TestableViteConfig = {
  plugins: [react()],
  
  // 路径别名
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@components': path.resolve(__dirname, './src/components'),
      '@pages': path.resolve(__dirname, './src/pages'),
      '@services': path.resolve(__dirname, './src/services'),
      '@types': path.resolve(__dirname, './src/types'),
      '@utils': path.resolve(__dirname, './src/utils'),
    },
  },
  
  // 开发服务器配置
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        // Docker容器内访问宿主机的API Gateway
        target: 'http://host.docker.internal:8888',
        changeOrigin: true,
        rewrite: (proxyPath: string) => proxyPath.replace(/^\/api/, ''),
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            console.log('proxy error', err);
          });
          proxy.on('proxyReq', (_proxyReq, req, _res) => {
            console.log('Sending Request to the Target:', req.method, req.url);
          });
          proxy.on('proxyRes', (proxyRes, req, _res) => {
            console.log('Received Response from the Target:', proxyRes.statusCode, req.url);
          });
        },
      },
    },
  },
  
  // 构建配置
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'antd-vendor': ['antd', '@ant-design/pro-components', '@ant-design/charts'],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
  
  // 优化依赖预构建
  optimizeDeps: {
    include: ['react', 'react-dom', 'antd', '@ant-design/pro-components'],
  },

  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
    },
  },
};

export default defineConfig(config);
