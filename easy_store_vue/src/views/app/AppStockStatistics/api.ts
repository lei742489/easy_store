import axios from 'axios';

export interface StockStatisticsQuery {
  categoryId?: number | string;
  key?: string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface StockStatisticsRecord {
  rowNo?: number;
  goodsId?: number;
  goodsName?: string;
  unit?: string;
  openingQty?: number;
  openingAmount?: number;
  inQty?: number;
  inAmount?: number;
  outQty?: number;
  outAmount?: number;
  endingQty?: number;
  endingAmount?: number;
  isSummary?: boolean;
}

export interface StockStatisticsResult {
  current?: number;
  pageSize?: number;
  total?: number;
  pages?: number;
  openingQtyTotal?: number;
  openingAmountTotal?: number;
  inQtyTotal?: number;
  inAmountTotal?: number;
  outQtyTotal?: number;
  outAmountTotal?: number;
  endingQtyTotal?: number;
  endingAmountTotal?: number;
  records?: StockStatisticsRecord[];
}

export function listStockStatistics(data: StockStatisticsQuery) {
  return axios.post<StockStatisticsResult>(
    'api/user/appGoods/stockStatistics',
    data
  );
}
