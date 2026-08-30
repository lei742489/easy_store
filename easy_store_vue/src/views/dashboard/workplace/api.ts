import axios from 'axios';

export interface HomeStatistics {
  date?: string;
  salesAmount?: number;
  salesAmountYesterday?: number;
  profitAmount?: number;
  profitAmountYesterday?: number;
  purchaseAmount?: number;
  purchaseAmountYesterday?: number;
  stockTotal?: number;
  stockTotalYesterday?: number;
}

export function getTodayHomeStatistics() {
  return axios.post<HomeStatistics>('api/user/appHomeStatistics/today', {});
}
