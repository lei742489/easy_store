import { defineStore } from 'pinia';
import {
  login as userLogin,
  logout as userLogout,
  getUserInfo,
  LoginData,
  register,
  RegData,
} from '@/api/user';
import { setToken, clearToken } from '@/utils/auth';
import { removeRouteListener } from '@/utils/route-listener';
import { Input, Modal } from '@arco-design/web-vue';
import { h } from 'vue';

import { UserState } from './types';
import useAppStore from '../app';

const USER_INFO_STORAGE_KEY = 'easy-store-user-info';

const defaultUserState = (): UserState => ({
  id: undefined,
  userName: undefined,
  realName: undefined,
  name: undefined,
  isRoot: undefined,
  avatar: undefined,
  mobile: undefined,
  job: undefined,
  organization: undefined,
  location: undefined,
  email: undefined,
  introduction: undefined,
  personalWebsite: undefined,
  jobName: undefined,
  organizationName: undefined,
  companyName: undefined,
  address: undefined,
  locationName: undefined,
  phone: undefined,
  registrationDate: undefined,
  accountId: undefined,
  certification: undefined,
  createTime: undefined,
  permissionCodes: [],
  role: '',
});

const normalizeUserState = (partial: Partial<UserState>): Partial<UserState> => {
  const userInfo = { ...partial };
  if (userInfo.isRoot !== undefined && userInfo.isRoot !== null) {
    userInfo.isRoot = Number(userInfo.isRoot);
  }
  return userInfo;
};

const loadCachedUserState = (): Partial<UserState> => {
  if (typeof window === 'undefined') return {};

  try {
    const raw = window.localStorage.getItem(USER_INFO_STORAGE_KEY);
    return raw ? normalizeUserState(JSON.parse(raw) as Partial<UserState>) : {};
  } catch {
    return {};
  }
};

const saveCachedUserState = (state: UserState) => {
  if (typeof window === 'undefined') return;

  window.localStorage.setItem(USER_INFO_STORAGE_KEY, JSON.stringify(state));
};

const clearCachedUserState = () => {
  if (typeof window === 'undefined') return;

  window.localStorage.removeItem(USER_INFO_STORAGE_KEY);
};

const useUserStore = defineStore('user', {
  state: (): UserState => ({
    ...defaultUserState(),
    ...loadCachedUserState(),
  }),

  getters: {
    userInfo(state: UserState): UserState {
      return { ...state };
    },
  },

  actions: {
    switchRoles() {
      return new Promise((resolve) => {
        this.role = this.role === 'user' ? 'admin' : 'user';
        resolve(this.role);
      });
    },
    // Set user's information
    setInfo(partial: Partial<UserState>) {
      this.$patch(normalizeUserState(partial));
      saveCachedUserState(this.$state);
    },

    // Reset user's information
    resetInfo() {
      clearCachedUserState();
      this.$reset();
    },

    openEdit() {
      Modal.confirm({
        title: '提示',
        content: '首次登录，请先完善个人信息',
        simple: true,
        maskClosable: false,
        onOk: () => {},
        onCancel: () => {},
      });
    },
    // Get user's information
    async info() {
      const res = await getUserInfo();
      const userInfo = normalizeUserState(res.data);
      this.setInfo({
        ...userInfo,
        role: '*',
      });
      if (userInfo.isRoot === 1 && !userInfo.companyName) this.openEdit();
      return userInfo;
    },

    // Login
    async login(loginForm: LoginData) {
      try {
        const res = await userLogin(loginForm);
        // A new token must not inherit the previous account's permission cache.
        this.resetInfo();
        setToken(res.data.token);
      } catch (err) {
        this.resetInfo();
        clearToken();
        throw err;
      }
    },

    async register(data: RegData) {
      try {
        const res = await register(data);
        setToken(res.data.token);
      } catch (err) {
        clearToken();
        throw err;
      }
    },
    logoutCallBack() {
      const appStore = useAppStore();
      this.resetInfo();
      clearToken();
      removeRouteListener();
      appStore.clearServerMenu();
    },
    // Logout
    async logout() {
      this.logoutCallBack();
      /* try {
        await userLogout();
      } finally {
        this.logoutCallBack();
      } */
    },
  },
});

export default useUserStore;
