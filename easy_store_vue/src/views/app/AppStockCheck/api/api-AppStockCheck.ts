import axios from 'axios';
import type { PolicyParams } from '@/api/list';
import type { pageRecords } from '@/store/modules/app/types';
import type { AppStockCheck } from '../types/AppStockCheck';

export function add(data: AppStockCheck) {
  return axios.post<any>('api/user/appStockCheck/add', data);
}

export function edit(data: AppStockCheck) {
  return axios.post<any>('api/user/appStockCheck/edit', data);
}

export function remove(data: AppStockCheck) {
  return axios.post<any>('api/user/appStockCheck/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appStockCheck/listPage', param);
}

export function createOrderNo() {
  return axios.post<string>('api/user/appStockCheck/createOrderNo', {});
}
