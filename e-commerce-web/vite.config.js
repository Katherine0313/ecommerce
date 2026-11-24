import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';
import Components from 'unplugin-vue-components/vite';
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers';
import { AntdvLessPlugin, AntdvModifyVars } from 'stepin/lib/style/plugins';
const timestamp = new Date().getTime();
const prodRollupOptions = {
  output: {
    // 手动分包：vue 核心与 antd 拆独立 chunk，业务代码改动不影响其 hash，利于长期缓存
    manualChunks: {
      'vue-vendor': ['vue', 'vue-router', 'pinia'],
      'antd-vendor': ['ant-design-vue', '@ant-design/icons-vue'],
    },
    chunkFileNames: (chunk) => {
      return 'assets/' + chunk.name + '.[hash]' + '.' + timestamp + '.js';
    },
    assetFileNames: (asset) => {
      const name = asset.name;
      if (name && (name.endsWith('.css') || name.endsWith('.js'))) {
        const names = name.split('.');
        const extname = names.splice(names.length - 1, 1)[0];
        return `assets/${names.join('.')}.[hash].${timestamp}.${extname}`;
      }
      return 'assets/' + asset.name;
    },
  },
};
// vite 配置
export default ({ command, mode }) => {
  // 获取环境变量
  const env = loadEnv(mode, process.cwd());
  console.log(mode);
  return defineConfig({
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || env.VITE_API_URL || 'http://127.0.0.1:8080',
          ws: true,
          changeOrigin: true,
        },
        // 图片统一从后端 upload/image/ 取，不再使用前端 public/image 里的历史副本；
        // 生产环境由 Nginx 把 /image/** 反代到后端，这里只处理本地开发
        '/image': {
          target: env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
      hmr: true,
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    esbuild: {
      jsxFactory: 'h',
      jsxFragment: 'Fragment',
    },
    build: {
      chunkSizeWarningLimit: 2048,
      rollupOptions: mode === 'production' ? prodRollupOptions : {},
    },
    plugins: [
      vue({
        template: {
          transformAssetUrls: {
            img: ['src'],
            'a-avatar': ['src'],
            'stepin-view': ['logo-src', 'presetThemeList'],
            'a-card': ['cover'],
          },
        },
      }),
      Components({
        resolvers: [
          AntDesignVueResolver({
            importStyle: mode === 'development' ? false : 'less',
          }),
        ],
      }),
    ],
    css: {
      preprocessorOptions: {
        less: {
          plugins: [AntdvLessPlugin],
          modifyVars: AntdvModifyVars,
          javascriptEnabled: true,
        },
      },
    },
    base: env.VITE_BASE_URL,
  });
};
