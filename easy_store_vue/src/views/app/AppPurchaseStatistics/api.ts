import axios from 'axios';

export interface PurchaseStatisticsQuery {
  cashierId?: number | string;
  categoryId?: number | string;
  goodsKey?: string;
  supplierId?: number | string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface PurchaseStatisticsRecord {
  rowNo?: number | string;
  goodsId?: number | string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  amount?: number;
  isSummary?: boolean;
}

export interface PurchaseStatisticsResult {
  current?: number;
  pageSize?: number;
  total?: number;
  quantityTotal?: number;
  amountTotal?: number;
  records?: PurchaseStatisticsRecord[];
}

export interface PurchaseStatisticsDetail {
  rowNo?: number | string;
  orderNo?: string;
  businessDate?: string;
  supplierName?: string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  unitPrice?: number;
  amount?: number;
  discountAmount?: number;
  payableAmount?: number;
  note?: string;
  isSummary?: boolean;
}

export function listPurchaseStatistics(data: PurchaseStatisticsQuery) {
  return axios.post<PurchaseStatisticsResult>(
    'api/user/appPurchaseStatistics/list',
    data
  );
}

export function listPurchaseStatisticsDetail(
  data: PurchaseStatisticsQuery & { goodsId: number | string }
) {
  return axios.post<PurchaseStatisticsDetail[]>(
    'api/user/appPurchaseStatistics/detail',
    data
  );
}
