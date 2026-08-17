import { mergeConfig } from 'vite';
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
