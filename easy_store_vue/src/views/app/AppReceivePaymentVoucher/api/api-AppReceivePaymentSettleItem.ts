import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppReceivePaymentSettleItem } from '../types/AppReceivePaymentSettleItem';

export function add(data: AppReceivePaymentSettleItem) {
  return axios.post<any>('api/user/appReceivePaymentSettleItem/add', data);
}

export function edit(data: AppReceivePaymentSettleItem) {
  return axios.post<any>('api/user/appReceivePaymentSettleItem/edit', data);
}

export function remove(data: AppReceivePaymentSettleItem) {
  return axios.post<any>('api/user/appReceivePaymentSettleItem/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>(
    'api/user/appReceivePaymentSettleItem/listPage',
    param
  );
}

export function listByPaymentId(paymentId?: string) {
  return axios.post<AppReceivePaymentSettleItem[]>(
    'api/user/appReceivePaymentSettleItem/listByOrderId',
    { paymentId }
  );
}

export function exportXlsFile(data: AppReceivePaymentSettleItem) {
  exportXls('api/user/appReceivePaymentSettleItem/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appReceivePaymentSettleItem/importExcel', file);
}
