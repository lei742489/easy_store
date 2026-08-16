import axios from 'axios';
import type { AppGoods } from '@/views/app/goods/types/AppGoods';

export interface StockWarningQuery {
  supplierId?: number | string;
  current?: number;
  pageSize?: number;
}

export interface StockWarningPage {
  records?: AppGoods[];
  current?: number;
  size?: number;
  total?: number;
}

export function listStockWarnings(data: StockWarningQuery) {
  return axios.post<StockWarningPage>(
    'api/user/appGoods/stockWarningPage',
    data
  );
}
