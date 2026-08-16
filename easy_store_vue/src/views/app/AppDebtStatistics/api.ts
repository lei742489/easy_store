import axios from 'axios';

export interface DebtStatisticsQuery {
  customerId?: number | string;
  startDate?: string;
  endDate?: string;
}

export interface DebtStatisticsRecord {
  rowNo?: number | string;
  customerId?: number;
  customerName?: string;
  openingBalance?: number;
  receivableAmount?: number;
  receivedAmount?: number;
  roundingAmount?: string | number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface DebtStatisticsResult {
  openingTotal?: number;
  receivableTotal?: number;
  receivedTotal?: number;
  endingTotal?: number;
  records?: DebtStatisticsRecord[];
}

export function listDebtStatistics(data: DebtStatisticsQuery) {
  return axios.post<DebtStatisticsResult>(
    'api/user/appDebtStatistics/list',
    data
  );
}
