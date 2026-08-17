import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppReceivePaymentVoucher } from '../types/AppReceivePaymentVoucher';

export function add(data: AppReceivePaymentVoucher) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/add', data);
}

export function edit(data: AppReceivePaymentVoucher) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/edit', data);
}

export function remove(data: AppReceivePaymentVoucher) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/remove', data);
}

export function approve(data: { id?: number; ids?: number[] }) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/approve', data);
}

export function exportPdfFile(data: AppReceivePaymentVoucher) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/exportPdf', data);
}

export function exportEscpFile(data: AppReceivePaymentVoucher) {
  return axios.post<any>('api/user/appReceivePaymentVoucher/exportEscp', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>(
    'api/user/appReceivePaymentVoucher/listPage',
    param
  );
}

export function exportXlsFile(data: AppReceivePaymentVoucher) {
  exportXls('api/user/appReceivePaymentVoucher/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appReceivePaymentVoucher/importExcel', file);
}

export function createOrderNo() {
  return axios.post<string>(
    'api/user/appReceivePaymentVoucher/createOrderNo',
    {}
  );
}
