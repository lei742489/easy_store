import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type {
  AppAccountSettle,
  AppAccountSettleType,
} from '../types/AppAccountSettle';

export function add(data: AppAccountSettle) {
  return axios.post<any>('api/user/appAccountSettle/add', data);
}

export function edit(data: AppAccountSettle) {
  return axios.post<any>('api/user/appAccountSettle/edit', data);
}

export function remove(data: AppAccountSettle) {
  return axios.post<any>('api/user/appAccountSettle/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appAccountSettle/listPage', param);
}

export function exportXlsFile(data: AppAccountSettle) {
  exportXls('api/user/appAccountSettle/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appAccountSettle/importExcel', file);
}

export function list() {
  return axios.post<AppAccountSettle[]>('api/user/appAccountSettle/list', {});
}

export function listByType() {
  return axios.post<AppAccountSettleType[]>(
    'api/user/appAccountSettle/typeList',
    {}
  );
}
