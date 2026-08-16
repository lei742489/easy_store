import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppUser } from '../types/AppUser';

export function add(data: AppUser) {
  return axios.post<any>('api/user/add', data);
}

export function edit(data: AppUser) {
  return axios.post<any>('api/user/edit', data);
}

export function remove(data: AppUser) {
  return axios.post<any>('api/user/remove', data);
}

export function resetPwd(data: AppUser) {
  return axios.post<any>('api/user/resetPwd', data);
}

export function checkPwd(pwd: string) {
  return axios.post<any>('api/user/checkPwd', { pwd });
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/listPage', param);
}

export function list() {
  return axios.post<AppUser[]>('api/user/list', {});
}

export function exportXlsFile(data: AppUser) {
  exportXls('api/user/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/importExcel', file);
}
