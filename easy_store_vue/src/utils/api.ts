import request from '@/utils/request';

// 普通 POST 请求
export function post<T = any>(url: string, data?: any) {
  return request.post<T>(url, data, {
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

// 文件上传（支持 FormData）
export function uploadFile<T = any>(url: string, file: File | FormData) {
  const formData = file instanceof FormData ? file : new FormData();
  if (!(file instanceof FormData)) {
    formData.append('file', file);
  }

  return request.post<T>(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
