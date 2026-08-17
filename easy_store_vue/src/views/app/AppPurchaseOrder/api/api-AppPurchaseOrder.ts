import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppPurchaseOrder } from '../types/AppPurchaseOrder';

export function add(data: AppPurchaseOrder) {
  return axios.post<any>('api/user/appPurchaseOrder/add', data);
}

export function edit(data: AppPurchaseOrder) {
  return axios.post<any>('api/user/appPurchaseOrder/edit', data);
}

export function remove(data: AppPurchaseOrder) {
  return axios.post<any>('api/user/appPurchaseOrder/remove', data);
}

export function approve(data: { id?: number; ids?: number[] }) {
  return axios.post<any>('api/user/appPurchaseOrder/approve', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appPurchaseOrder/listPage', param);
}

export function exportXlsFile(data: AppPurchaseOrder) {
  exportXls('api/user/appPurchaseOrder/exportXls', data);
}

export function exportPdfFile(data: AppPurchaseOrder) {
  return axios.post<any>('api/user/appPurchaseOrder/exportPdf', data);
}

export function exportEscpFile(data: AppPurchaseOrder) {
  return axios.post<any>('api/user/appPurchaseOrder/exportEscp', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appPurchaseOrder/importExcel', file);
}

export function createOrderNo() {
  return axios.post<string>('api/user/appPurchaseOrder/createOrderNo', {});
}
