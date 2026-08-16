import axios from 'axios';
import { exportXls, PolicyParams } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { AppCustomerQuote } from '../types/AppCustomerQuote';

export function add(data: AppCustomerQuote) {
  return axios.post<any>('api/user/appCustomerQuote/add', data);
}

export function edit(data: AppCustomerQuote) {
  return axios.post<any>('api/user/appCustomerQuote/edit', data);
}

export function remove(data: AppCustomerQuote) {
  return axios.post<any>('api/user/appCustomerQuote/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appCustomerQuote/listPage', param);
}

export function batchAdd(rows: AppCustomerQuote[]) {
  return axios.post<any>('api/user/appCustomerQuote/batchAdd', { rows });
}

export function batchUpdatePrice(ids: string, quotePrice: number) {
  return axios.post<any>('api/user/appCustomerQuote/batchUpdatePrice', {
    ids,
    quotePrice,
  });
}

export function batchRemove(ids: string) {
  return axios.post<any>('api/user/appCustomerQuote/batchRemove', { ids });
}

export function getQuoteByCustomerAndGoods(
  customerId: number | string,
  goodsId: number | string
) {
  return axios.post<AppCustomerQuote | null>(
    'api/user/appCustomerQuote/getQuoteByCustomerAndGoods',
    {
      customerId,
      goodsId,
    }
  );
}

export function exportXlsFile(data: AppCustomerQuote) {
  exportXls('api/user/appCustomerQuote/exportXls', data);
}
