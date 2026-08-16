import axios from 'axios';

export interface PayableReportQuery {
  supplierId?: number | string;
  startDate?: string;
  endDate?: string;
}

export interface PayableDetailRecord {
  rowNo?: number | string;
  businessDate?: string;
  orderNo?: string;
  summary?: string;
  payableAmount?: number;
  paidAmount?: number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface PayableStatementRecord {
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
  payableAmount?: number;
  paidAmount?: number;
  roundingAmount?: string | number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface PayableStatisticsRecord {
  rowNo?: number | string;
  supplierId?: number;
  supplierName?: string;
  openingBalance?: number;
  payableAmount?: number;
  paidAmount?: number;
  roundingAmount?: string | number;
  endingBalance?: number;
  isSummary?: boolean;
}

export interface PayableReportResult<T> {
  supplierId?: number;
  supplierName?: string;
  openingBalance?: number;
  payableTotal?: number;
  paidTotal?: number;
  endingBalance?: number;
  openingTotal?: number;
  endingTotal?: number;
  records?: T[];
}

export function listPayableDetail(data: PayableReportQuery) {
  return axios.post<PayableReportResult<PayableDetailRecord>>(
    'api/user/appPayableReport/detail',
    data
  );
}

export function listPayableStatement(data: PayableReportQuery) {
  return axios.post<PayableReportResult<PayableStatementRecord>>(
    'api/user/appPayableReport/statement',
    data
  );
}

export function listPayableStatistics(data: PayableReportQuery) {
  return axios.post<PayableReportResult<PayableStatisticsRecord>>(
    'api/user/appPayableReport/statistics',
    data
  );
}
