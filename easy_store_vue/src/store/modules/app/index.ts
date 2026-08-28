import { defineStore } from 'pinia';
import { Notification } from '@arco-design/web-vue';
import type { NotificationReturn } from '@arco-design/web-vue/es/notification/interface';
import type { RouteRecordNormalized } from 'vue-router';
import defaultSettings from '@/config/settings.json';
import { getMenuList } from '@/api/user';
import { AppState } from './types';

const THEME_COLOR_STORAGE_KEY = 'easy-store-theme-color';
const THEME_MODE_STORAGE_KEY = 'easy-store-theme-mode';

export const THEME_COLOR_PRESETS = [
  { label: '红色', value: '#F53F3F', palette: 'red' },
  { label: '橙红色', value: '#F77234', palette: 'orangered' },
  { label: '橙色', value: '#FF7D00', palette: 'orange' },
  { label: '青色', value: '#14C9C9', palette: 'cyan' },
  { label: '绿色', value: '#00B42A', palette: 'green' },
  { label: '蓝色', value: '#165DFF', palette: 'arcoblue' },
  { label: '浅蓝色', value: '#3491FA', palette: 'blue' },
  { label: '紫色', value: '#722ED1', palette: 'purple' },
] as const;

function resolveThemeColor(color?: string) {
  return (
    THEME_COLOR_PRESETS.find((item) => item.value === color) ||
    THEME_COLOR_PRESETS.find(
      (item) => item.value === defaultSettings.themeColor
    ) ||
    THEME_COLOR_PRESETS[7]
  );
}

function getStoredThemeColor() {
  if (typeof window === 'undefined') return defaultSettings.themeColor;
  return (
    window.localStorage.getItem(THEME_COLOR_STORAGE_KEY) ||
    defaultSettings.themeColor
  );
}

function getStoredThemeMode() {
  if (typeof window === 'undefined') return defaultSettings.theme;
  return (
    window.localStorage.getItem(THEME_MODE_STORAGE_KEY) || defaultSettings.theme
  );
}

const useAppStore = defineStore('app', {
  state: (): AppState => ({
    ...defaultSettings,
    theme: getStoredThemeMode(),
    themeColor: getStoredThemeColor(),
  }),

  getters: {
    appCurrentSetting(state: AppState): AppState {
      return { ...state };
    },
    appDevice(state: AppState) {
      return state.device;
    },
    appAsyncMenus(state: AppState): RouteRecordNormalized[] {
      return state.serverMenu as unknown as RouteRecordNormalized[];
    },
  },

  actions: {
    // Update app settings
    updateSettings(partial: Partial<AppState>) {
      // @ts-ignore-next-line
      this.$patch(partial);
    },

    // Change theme color
    toggleTheme(dark: boolean) {
      if (dark) {
        this.theme = 'dark';
        document.body.setAttribute('arco-theme', 'dark');
      } else {
        this.theme = 'light';
        document.body.removeAttribute('arco-theme');
      }
      window.localStorage.setItem(THEME_MODE_STORAGE_KEY, this.theme);
    },
    applyThemeColor(color = this.themeColor) {
      const themeColor = resolveThemeColor(color);
      this.themeColor = themeColor.value;
      for (let index = 1; index <= 10; index += 1) {
        document.body.style.setProperty(
          `--primary-${index}`,
          `var(--${themeColor.palette}-${index})`
        );
        document.body.style.setProperty(
          `--link-${index}`,
          `var(--${themeColor.palette}-${index})`
        );
      }
    },
    setThemeColor(color: string) {
      this.applyThemeColor(color);
      window.localStorage.setItem(THEME_COLOR_STORAGE_KEY, this.themeColor);
    },
    toggleDevice(device: string) {
      this.device = device;
    },
    toggleMenu(value: boolean) {
      this.hideMenu = value;
    },
    async fetchServerMenuConfig() {
      let notifyInstance: NotificationReturn | null = null;
      try {
        notifyInstance = Notification.info({
          id: 'menuNotice', // Keep the instance id the same
          content: 'loading',
          closable: true,
        });
        const { data } = await getMenuList();
        this.serverMenu = data;
        notifyInstance = Notification.success({
          id: 'menuNotice',
          content: 'success',
          closable: true,
        });
      } catch (error) {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        notifyInstance = Notification.error({
          id: 'menuNotice',
          content: 'error',
          closable: true,
        });
      }
    },
    clearServerMenu() {
      this.serverMenu = [];
    },
  },
});

export default useAppStore;
