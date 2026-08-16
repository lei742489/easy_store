import axios from 'axios';

export interface ProfitStatisticsQuery {
  customerId?: number | string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface ProfitStatisticsRecord {
  rowNo?: number | string;
  customerId?: number | string;
  customerName?: string;
  discountedAmount?: number;
  costAmount?: number;
  profitAmount?: number;
  profitRate?: number;
  isSummary?: boolean;
}

export interface ProfitStatisticsResult {
  current?: number;
  pageSize?: number;
  total?: number;
  discountedTotal?: number;
  costTotal?: number;
  profitTotal?: number;
  profitRateTotal?: number;
  records?: ProfitStatisticsRecord[];
}

export interface ProfitStatisticsDetail {
  rowNo?: number | string;
  businessDate?: string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  discountedUnitPrice?: number;
  discountedAmount?: number;
  costAmount?: number;
  profitAmount?: number;
  profitRate?: number;
  isSummary?: boolean;
}

export function listProfitStatistics(data: ProfitStatisticsQuery) {
  return axios.post<ProfitStatisticsResult>(
    'api/user/appProfitStatistics/list',
    data
  );
}

export function listProfitStatisticsDetail(data: ProfitStatisticsQuery) {
  return axios.post<ProfitStatisticsDetail[]>(
    'api/user/appProfitStatistics/detail',
    data
  );
}
