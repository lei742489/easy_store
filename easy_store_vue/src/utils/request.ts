import axios, { AxiosResponse } from 'axios';
import { Message } from '@arco-design/web-vue';

const DEV_TYPE = 'web';
const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'web';

const request = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL}/api`,
  timeout: 30000,
});

request.interceptors.request.use(
  (config) => {
    config.headers = {
      ...(config.headers || {}),
      devType: DEV_TYPE,
      appVersion: APP_VERSION,
    } as any;
    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response;
    if (data.code !== 0) {
      Message.error(data.message || '请求错误');
      return Promise.reject(data);
    }

    return data;
  },
  (error) => {
    Message.error(error.message || '网络异常');
    return Promise.reject(error);
  }
);

export default request;
