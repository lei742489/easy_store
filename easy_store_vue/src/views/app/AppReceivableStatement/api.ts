import axios from 'axios';

export interface ReceivableStatementQuery {
  customerId?: number | string;
  startDate?: string;
  endDate?: string;
}

export interface ReceivableStatementRecord {
  rowNo?: number | string;
  rowType?: string;
  goodsName?: string;
  unit?: string;
  quantity?: number;
  unitPrice?: number;
  freightAmount?: number;
  totalAmount?: number;
  discountAmount?: number;
  note?: string;
  receivableAmount?: number;
  receivedAmount?: number;
  roundingAmount?: string | number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface ReceivableStatementResult {
  customerId?: number;
  customerName?: string;
  openingBalance?: number;
  receivableTotal?: number;
  receivedTotal?: number;
  endingBalance?: number;
  records?: ReceivableStatementRecord[];
}

export function listReceivableStatement(data: ReceivableStatementQuery) {
  return axios.post<ReceivableStatementResult>(
    'api/user/appReceivableStatement/list',
    data
  );
}
