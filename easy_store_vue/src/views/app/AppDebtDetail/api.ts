import axios from 'axios';

export interface DebtDetailQuery {
  customerId?: number | string;
  startDate?: string;
  endDate?: string;
}

export interface DebtDetailRecord {
  rowNo?: number | string;
  businessDate?: string;
  orderNo?: string;
  summary?: string;
  receivableAmount?: number;
  receivedAmount?: number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface DebtDetailResult {
  customerId?: number;
  customerName?: string;
  openingBalance?: number;
  receivableTotal?: number;
  receivedTotal?: number;
  endingBalance?: number;
  records?: DebtDetailRecord[];
}

export function listDebtDetail(data: DebtDetailQuery) {
  return axios.post<DebtDetailResult>('api/user/appDebtDetail/list', data);
}
