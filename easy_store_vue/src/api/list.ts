import axios from 'axios';
import qs from 'query-string';
import type { DescData } from '@arco-design/web-vue/es/descriptions/interface';
import { HttpResponse } from '@/api/interceptor';
import { useUserStore } from '@/store';

export interface PolicyRecord {
  id: string;
  number: number;
  name: string;
  contentType: 'img' | 'horizontalVideo' | 'verticalVideo';
  filterType: 'artificial' | 'rules';
  count: number;
  status: 'online' | 'offline';
  createdTime: string;
}

export interface PolicyParams extends Partial<PolicyRecord> {
  current: number;
  pageSize: number;
  column?: string;
  order?: string;
  isRoot?: number;
}

export interface PolicyListRes {
  list: PolicyRecord[];
  total: number;
}

export function queryPolicyList(params: PolicyParams) {
  return axios.get<PolicyListRes>('/api/list/policy', {
    params,
    paramsSerializer: (obj) => {
      return qs.stringify(obj);
    },
  });
}

export interface ServiceRecord {
  id: number;
  title: string;
  description: string;
  name?: string;
  actionType?: string;
  icon?: string;
  data?: DescData[];
  enable?: boolean;
  expires?: boolean;
}
export function queryInspectionList() {
  return axios.get('/api/list/quality-inspection');
}

export function queryTheServiceList() {
  return axios.get('/api/list/the-service');
}

export function queryRulesPresetList() {
  return axios.get('/api/list/rules-preset');
}
const apiBaseUrl: string = String(import.meta.env.VITE_API_BASE_URL || '')
  .trim()
  .replace(/^['"]|['"]$/g, '');

export function exportXls(downUrl: string, rawParams: Record<string, any>) {
  // 过滤掉空参数
  const filteredParams: Record<string, any> = {};
  const userStore = useUserStore();
  if (userStore.id !== undefined && userStore.id !== null) {
    filteredParams.userId = userStore.id;
  }
  if (userStore.isRoot !== undefined && userStore.isRoot !== null) {
    filteredParams.isRoot = userStore.isRoot;
  }
  Object.keys(rawParams).forEach((key) => {
    const value = rawParams[key];
    if (value !== undefined && value !== null && value !== '') {
      filteredParams[key] = value;
    }
  });

  // 拼接为完整的 GET 请求链接
  const queryString = new URLSearchParams(filteredParams).toString();
  const fullUrl = `/${downUrl}?${queryString}`;
  const downloadUrl = `${apiBaseUrl || window.location.origin}${fullUrl}`;

  if (window.electronAPI?.openExternal) {
    window.electronAPI.openExternal(downloadUrl);
    return;
  }

  // 浏览器环境直接打开导出链接
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/**
 * 上传文件
 * @param url 接口地址
 * @param file 要上传的文件对象（File）
 * @param extraData 可选的额外字段
 */
export function uploadFile(
  url: string,
  file: File,
  extraData: Record<string, any> = {}
) {
  const formData = new FormData();

  formData.append('file', file); // 文件字段名根据后端定义，常用 "file"

  // 如果有额外参数，一并添加
  Object.keys(extraData).forEach((key) => {
    formData.append(key, extraData[key]);
  });

  return axios.post<HttpResponse>(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
