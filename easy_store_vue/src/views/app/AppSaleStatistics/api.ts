import axios from 'axios';

export interface SaleStatisticsQuery {
  cashierId?: number | string;
  categoryId?: number | string;
  goodsKey?: string;
  customerId?: number | string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface SaleStatisticsRecord {
  rowNo?: number | string;
  goodsId?: number | string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  amount?: number;
  isSummary?: boolean;
}

export interface SaleStatisticsResult {
  current?: number;
  pageSize?: number;
  total?: number;
  quantityTotal?: number;
  amountTotal?: number;
  records?: SaleStatisticsRecord[];
}

export interface SaleStatisticsDetail {
  rowNo?: number | string;
  orderNo?: string;
  businessDate?: string;
  customerName?: string;
  cashierName?: string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  unitPrice?: number;
  amount?: number;
  discountAmount?: number;
  receivableAmount?: number;
  note?: string;
  isSummary?: boolean;
}

export function listSaleStatistics(data: SaleStatisticsQuery) {
  return axios.post<SaleStatisticsResult>(
    'api/user/appSaleStatistics/list',
    data
  );
}

export function listSaleStatisticsDetail(
  data: SaleStatisticsQuery & { goodsId: number | string }
) {
  return axios.post<SaleStatisticsDetail[]>(
    'api/user/appSaleStatistics/detail',
    data
  );
}
