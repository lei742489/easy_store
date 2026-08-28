import { createApp } from 'vue';
import ArcoVue from '@arco-design/web-vue';
import ArcoVueIcon from '@arco-design/web-vue/es/icon';
import globalComponents from '@/components';
import print from 'vue3-print-nb';
import router from './router';
import store, { useAppStore } from './store';
import i18n from './locale';
import directive from './directive';
import './mock';
import App from './App.vue';
// Styles are imported via arco-plugin. See config/plugin/arcoStyleImport.ts in the directory for details
// 样式通过 arco-plugin 插件导入。详见目录文件 config/plugin/arcoStyleImport.ts
// https://arco.design/docs/designlab/use-theme-package
import '@/assets/style/global.less';
import '@/api/interceptor';
import appConfig from './config/app';

document.title = appConfig.appName;

const app = createApp(App);

app.use(ArcoVue, {});
app.use(ArcoVueIcon);
app.use(print);
app.use(router);
app.use(store);
const appStore = useAppStore();
appStore.applyThemeColor();
appStore.toggleTheme(appStore.theme === 'dark');
app.use(i18n);
app.use(globalComponents);
app.use(directive);

app.mount('#app');
