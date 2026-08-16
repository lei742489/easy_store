import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppPaymentSettleItem } from '../types/AppPaymentSettleItem';

export function add(data: AppPaymentSettleItem) {
  return axios.post<any>('api/user/appPaymentSettleItem/add', data);
}

export function edit(data: AppPaymentSettleItem) {
  return axios.post<any>('api/user/appPaymentSettleItem/edit', data);
}

export function remove(data: AppPaymentSettleItem) {
  return axios.post<any>('api/user/appPaymentSettleItem/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>(
    'api/user/appPaymentSettleItem/listPage',
    param
  );
}

export function listByPaymentId(paymentId?: string) {
  return axios.post<AppPaymentSettleItem[]>(
    'api/user/appPaymentSettleItem/listByOrderId',
    { paymentId }
  );
}

export function exportXlsFile(data: AppPaymentSettleItem) {
  exportXls('api/user/appPaymentSettleItem/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appPaymentSettleItem/importExcel', file);
}
