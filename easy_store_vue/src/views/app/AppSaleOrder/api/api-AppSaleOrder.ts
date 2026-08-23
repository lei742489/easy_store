import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppSaleOrder } from '../types/AppSaleOrder';

export function add(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/add', data);
}

export function edit(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/edit', data);
}

export function remove(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/remove', data);
}

export function approve(data: { id?: number; ids?: number[] }) {
  return axios.post<any>('api/user/appSaleOrder/approve', data);
}

export function recalculateProfit(data: { id?: number; ids?: number[] }) {
  return axios.post<any>('api/user/appSaleOrder/recalculateProfit', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appSaleOrder/listPage', param);
}

export function exportXlsFile(data: AppSaleOrder) {
  exportXls('api/user/appSaleOrder/exportXls', data);
}

export function exportPdfFile(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/exportPdf', data);
}

export function exportHtmlFile(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/exportHtml', data);
}

export function exportEscpFile(data: AppSaleOrder) {
  return axios.post<any>('api/user/appSaleOrder/exportEscp', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appSaleOrder/importExcel', file);
}

export function createOrderNo() {
  return axios.post<string>('api/user/appSaleOrder/createOrderNo', {});
}
