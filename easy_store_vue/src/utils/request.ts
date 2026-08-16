// src/utils/request.ts
import axios, { AxiosRequestConfig, AxiosResponse } from 'axios';
import { Message } from '@arco-design/web-vue';

// 创建 Axios 实例
const request = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL} /api`, // 可配置 .env 文件
  timeout: 10000,
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response;

    // 根据业务处理错误
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
