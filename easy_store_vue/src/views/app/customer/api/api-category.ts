import axios from 'axios';
import type { categoryType } from '../types/category';

export function add(data: categoryType) {
  return axios.post<any>('api/user/customer/category/add', data);
}

export function edit(data: categoryType) {
  return axios.post<any>('api/user/customer/category/edit', data);
}

export function remove(data: categoryType) {
  return axios.post<any>('api/user/customer/category/remove', data);
}

export function list() {
  return axios.post<categoryType[]>('api/user/customer/category/list', {});
}
