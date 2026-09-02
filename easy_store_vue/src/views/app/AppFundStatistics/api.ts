import axios from 'axios';

export interface FundStatisticsItem {
  id?: number;
  itemKey?: string;
  name?: string;
  itemType?: 'income' | 'expense' | 'mixed';
  participatePerformance?: boolean | number;
  disabled?: boolean | number;
  fixed?: boolean;
}

export interface FundStatisticsQuery {
  itemType?: 'income' | 'expense';
  itemKey?: string;
  startDate?: string;
  endDate?: string;
  userId?: number | string;
}

export interface FundStatisticsRecord {
  rowNo?: number | string;
  itemKey?: string;
  itemName?: string;
  itemType?: 'income' | 'expense' | 'mixed';
  income?: number;
  expense?: number;
  isSummary?: boolean;
}

export interface FundStatisticsResult {
  incomeTotal?: number;
  expenseTotal?: number;
  netTotal?: number;
  records?: FundStatisticsRecord[];
}

export interface FundStatisticsDetail {
  rowNo?: number | string;
  businessTime?: number;
  businessDate?: string;
  orderNo?: string;
  summary?: string;
  counterparty?: string;
  income?: number;
  expense?: number;
  isSummary?: boolean;
}

export interface FundStatisticsDetailResult {
  current?: number;
  pageSize?: number;
  total?: number;
  incomeTotal?: number;
  expenseTotal?: number;
  netTotal?: number;
  records?: FundStatisticsDetail[];
}

export function listFundStatisticsItems(data?: {
  itemType?: 'income' | 'expense';
  userId?: number | string;
}) {
  return axios.post<FundStatisticsItem[]>(
    'api/user/appFundStatistics/items',
    data || {}
  );
}

export function listFundStatistics(data: FundStatisticsQuery) {
  return axios.post<FundStatisticsResult>(
    'api/user/appFundStatistics/list',
    data
  );
}

export function listFundStatisticsDetail(
  data: Pick<FundStatisticsQuery, 'startDate' | 'endDate'> & {
    itemKey: string;
    userId?: number | string;
    current?: number;
    pageSize?: number;
  }
) {
  return axios.post<FundStatisticsDetailResult>(
    'api/user/appFundStatistics/detail',
    data
  );
}
