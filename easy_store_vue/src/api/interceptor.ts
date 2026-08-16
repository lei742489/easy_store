import axios from 'axios';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { Message, Modal } from '@arco-design/web-vue';
import { useUserStore } from '@/store';
import { getToken } from '@/utils/auth';
// import { openPasswordModal } from '@/api/passwordVerification';
// import { checkPwd } from '@/views/app/AppUser/api/api-AppUser';

export interface HttpResponse<T = unknown> {
  status: number;
  message: string;
  code: number;
  success: boolean;
  data: T;
}

if (import.meta.env.VITE_API_BASE_URL) {
  axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
  axios.defaults.timeout = 6000; // 超时时间 5 秒
}

axios.interceptors.request.use(
  async (config: AxiosRequestConfig) => {
    // let each request carry token
    // this example using the JWT token
    // Authorization is a custom headers key
    // please modify it according to the actual situation
    const token = getToken();
    if (token) {
      if (!config.headers) {
        config.headers = {};
      }
      config.headers.token = `${token}`;
    }

    /* if (config.url?.endsWith('remove')) {
      const pwd = await openPasswordModal();
      try {
        await checkPwd(pwd);
      } catch (err) {
        return Promise.reject();
      }
    } */
    return config;
  },
  (error) => {
    // do something
    return Promise.reject(error);
  }
);
// add response interceptors
axios.interceptors.response.use(
  (response: AxiosResponse<HttpResponse>) => {
    const res = response.data;
    const method = response.config.method?.toUpperCase();
    // if the custom code is not 20000, it is judged as an error.

    if (!res.success && method !== 'GET') {
      Message.error({
        content: res.message || '请求错误',
        duration: 5 * 1000,
      });
      // 50008: Illegal token; 50012: Other clients logged in; 50014: Token expired;
      if (
        [403, 50012, 50014].includes(res.code) &&
        response.config.url !== '/api/user/info'
      ) {
        Modal.error({
          title: '提示',
          content: '登录过期，请重新登录',
          okText: '重新登录',
          async onOk() {
            const userStore = useUserStore();
            await userStore.logout();
            window.location.reload();
          },
        });
      }
      return Promise.reject(new Error(res.message || '系统错误'));
    }
    return res;
  },
  (error) => {
    if (error)
      Message.error({
        content: error,
        duration: 5 * 1000,
      });

    return Promise.reject(error);
  }
);
