import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppPaymentVoucher } from '../types/AppPaymentVoucher';

export function add(data: AppPaymentVoucher) {
  return axios.post<any>('api/user/appPaymentVoucher/add', data);
}

export function edit(data: AppPaymentVoucher) {
  return axios.post<any>('api/user/appPaymentVoucher/edit', data);
}

export function remove(data: AppPaymentVoucher) {
  return axios.post<any>('api/user/appPaymentVoucher/remove', data);
}

export function approve(data: { id?: number; ids?: number[] }) {
  return axios.post<any>('api/user/appPaymentVoucher/approve', data);
}

export function exportPdfFile(data: AppPaymentVoucher) {
  return axios.post<any>('api/user/appPaymentVoucher/exportPdf', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appPaymentVoucher/listPage', param);
}

export function exportXlsFile(data: AppPaymentVoucher) {
  exportXls('api/user/appPaymentVoucher/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appPaymentVoucher/importExcel', file);
}

export function createOrderNo() {
  return axios.post<string>('api/user/appPaymentVoucher/createOrderNo', {});
}
