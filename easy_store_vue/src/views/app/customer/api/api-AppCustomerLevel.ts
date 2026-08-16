import axios from 'axios';
import { PolicyParams, exportXls } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppCustomerLevel } from '../types/AppCustomerLevel';

export function add(data: AppCustomerLevel) {
  return axios.post<any>('api/user/customer/level/add', data);
}

export function edit(data: AppCustomerLevel) {
  return axios.post<any>('api/user/customer/level/edit', data);
}

export function remove(data: AppCustomerLevel) {
  return axios.post<any>('api/user/customer/level/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/customer/level/listPage', param);
}

export function list() {
  return axios.post<AppCustomerLevel[]>('api/user/customer/level/list', {});
}
