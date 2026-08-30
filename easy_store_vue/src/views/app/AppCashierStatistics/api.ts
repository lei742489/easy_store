import axios from 'axios';

export interface CashierStatisticsQuery {
  cashierId?: number | string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface CashierStatisticsRecord {
  rowNo?: number | string;
  cashierId?: number | string;
  cashierName?: string;
  commissionRate?: number;
  quantity?: number;
  salesAmount?: number;
  costAmount?: number;
  profitAmount?: number;
  commissionAmount?: number;
  profitRate?: number;
  isSummary?: boolean;
}

export interface CashierStatisticsResult {
  current?: number;
  pageSize?: number;
  total?: number;
  quantityTotal?: number;
  salesTotal?: number;
  costTotal?: number;
  profitTotal?: number;
  commissionTotal?: number;
  profitRateTotal?: number;
  records?: CashierStatisticsRecord[];
}

export interface CashierStatisticsDetail {
  rowNo?: number | string;
  groupId?: number | string;
  groupName?: string;
  unit?: string;
  quantity?: number;
  salesAmount?: number;
  costAmount?: number;
  profitAmount?: number;
  commissionAmount?: number;
  profitRate?: number;
  isSummary?: boolean;
}

export type CashierStatisticsPeriodType = 'day' | 'month' | 'range';

export interface CashierStatisticsPeriodQuery extends CashierStatisticsQuery {
  statisticsType: CashierStatisticsPeriodType;
}

export interface CashierStatisticsPeriodRecord {
  rowNo?: number | string;
  date?: string;
  quantity?: number;
  salesAmount?: number;
  costAmount?: number;
  profitAmount?: number;
  commissionAmount?: number;
  profitRate?: number;
  isSummary?: boolean;
}

export function listCashierStatistics(data: CashierStatisticsQuery) {
  return axios.post<CashierStatisticsResult>(
    'api/user/appCashierStatistics/list',
    data
  );
}

export function listCashierStatisticsDetail(
  data: CashierStatisticsQuery & { groupBy: 'customer' | 'goods' }
) {
  return axios.post<CashierStatisticsDetail[]>(
    'api/user/appCashierStatistics/detail',
    data
  );
}

export function listCashierStatisticsPeriod(
  data: CashierStatisticsPeriodQuery
) {
  return axios.post<CashierStatisticsPeriodRecord[]>(
    'api/user/appCashierStatistics/period',
    data
  );
}
