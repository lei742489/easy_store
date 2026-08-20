import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
import type { AppGoods, AppUnit } from '../types/AppGoods';

export function add(data: AppGoods) {
  return axios.post<any>('api/user/appGoods/add', data);
}

export function edit(data: AppGoods) {
  return axios.post<any>('api/user/appGoods/edit', data);
}

export function remove(data: AppGoods) {
  return axios.post<any>('api/user/appGoods/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appGoods/listPage', param);
}

export function listQuotePage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appGoods/listQuotePage', param);
}

export function getUnitList() {
  return axios.post<AppUnit[]>('api/user/appUnit/list', {});
}

export function exportXlsFile(data: AppGoods) {
  exportXls('api/user/appGoods/exportXls', data);
}

export function importExcel(file: File) {
  return uploadFile('api/user/appGoods/importExcel', file);
}

export async function searchKey(key: string, pageNo?: number) {
  const response = await axios.post<GoodsSearchResult[]>(
    'api/user/appGoods/searchKey',
    {
      key,
      pageNo,
    }
  );
  return {
    ...response,
    data: (response.data || []).map((item) => ({
      ...item,
      label: item.value || item.label || '',
    })),
  };
}

export function stockDetail(data: {
  goodsId: number;
  startDate?: string;
  endDate?: string;
}) {
  return axios.post<any>('api/user/appGoods/stockDetail', data);
}

export function batchUpdateCategory(categoryId: number, ids: string) {
  return axios.post<any>('api/user/appGoods/batchUpdateCategory', {
    ids,
    categoryId,
  });
}

export function batchChangeStatus(status: number, ids: string) {
  return axios.post<any>('api/user/appGoods/batchChangeStatus', {
    ids,
    status,
  });
}

export function batchRemove(ids: string) {
  return axios.post<any>('api/user/appGoods/batchRemove', {
    ids,
  });
}
