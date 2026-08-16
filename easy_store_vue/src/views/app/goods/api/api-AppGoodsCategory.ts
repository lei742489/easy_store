import axios from 'axios';
import type { AppGoodsCategory } from '../types/AppGoodsCategory';

export function add(data: AppGoodsCategory) {
  return axios.post<any>('api/user/goods/category/add', data);
}

export function edit(data: AppGoodsCategory) {
  return axios.post<any>('api/user/goods/category/edit', data);
}

export function remove(data: AppGoodsCategory) {
  return axios.post<any>('api/user/goods/category/remove', data);
}

export function list() {
  return axios.post<AppGoodsCategory[]>('api/user/goods/category/list', {});
}
