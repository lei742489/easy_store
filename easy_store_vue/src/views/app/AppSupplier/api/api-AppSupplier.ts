import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppSupplier } from '../types/AppSupplier';

export function add(data: AppSupplier) {
  return axios.post<any>('api/user/appSupplier/add', data);
}

export function edit(data: AppSupplier) {
  return axios.post<any>('api/user/appSupplier/edit', data);
}

export function remove(data: AppSupplier) {
  return axios.post<any>('api/user/appSupplier/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appSupplier/listPage', param);
}

export function list(key?: string) {
  return axios.post<AppSupplier[]>('api/user/appSupplier/list', { key });
}

export function exportXlsFile(data: AppSupplier) {
  exportXls('api/user/appSupplier/exportXls', data);
}

export function refreshPayable() {
  return axios.post<number>('api/user/appSupplier/refreshPayable');
}

export function importExcel(file: File) {
  return uploadFile('api/user/appSupplier/importExcel', file);
}

export function searchKey(key: string) {
  return axios.post<string[]>('api/user/appSupplier/searchKey', { key });
}
