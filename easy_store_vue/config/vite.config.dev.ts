import { mergeConfig, defineConfig  } from 'vite';
import eslint from 'vite-plugin-eslint';
import baseConfig from './vite.config.base';

export default mergeConfig(
  {
    mode: 'development',
    server: {
      port: 8185,
      open: false,
      fs: {
        strict: true,
      },
    },

    plugins: [
      eslint({
        cache: false,
        include: ['src/**/*.ts', 'src/**/*.tsx', 'src/**/*.vue'],
        exclude: ['node_modules'],
      }),
    ],
    css: {
      preprocessorOptions: {
        less: {
          modifyVars: {
            'arcoblue-6': '#5941CD', // ✅ 替换 ArcoVue 主色
          },
          javascriptEnabled: true,
        },
      },
    },
  },
  baseConfig
);
