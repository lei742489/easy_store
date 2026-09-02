import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { Customer } from '../types/customer';

export function add(data: Customer) {
  return axios.post<any>('api/user/customer/add', data);
}

export function edit(data: Customer) {
  return axios.post<any>('api/user/customer/edit', data);
}

export function remove(data: Customer) {
  return axios.post<any>('api/user/customer/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/customer/listPage', param);
}

export function exportXlsFile(data: Customer) {
  exportXls('api/user/customer/exportXls', data);
}

export function refreshPayable() {
  return axios.post<number>('api/user/customer/refreshPayable');
}

export function importExcel(file: File) {
  return uploadFile('api/user/customer/importExcel', file);
}

export function searchKey(key: string) {
  return axios.post<string[]>('api/user/customer/searchKey', { key });
}

export function list(key?: string) {
  return axios.post<Customer[]>('api/user/customer/list', { key });
}
