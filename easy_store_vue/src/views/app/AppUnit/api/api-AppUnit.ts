import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppUnit } from '../types/AppUnit';

export function add(data: AppUnit) {
  return axios.post<any>('api/user/appUnit/add', data);
}

export function edit(data: AppUnit) {
  return axios.post<any>('api/user/appUnit/edit', data);
}

export function remove(data: AppUnit) {
  return axios.post<any>('api/user/appUnit/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appUnit/listPage', param);
}

export function exportXlsFile(data: AppUnit) {
  exportXls('api/user/appUnit/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appUnit/importExcel', file);
}
