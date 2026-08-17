import axios from 'axios';

export interface IncomeExpenseRecordQuery {
  customerId?: number | string;
  supplierId?: number | string;
  fundItem?: string;
  incomeExpenseType?: 'income' | 'expense';
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface IncomeExpenseRecord {
  rowNo?: number | string;
  businessDate?: string;
  orderNo?: string;
  summary?: string;
  counterparty?: string;
  fundItem?: string;
  income?: number;
  expense?: number;
  balance?: number;
  isSummary?: boolean;
}

export interface IncomeExpenseRecordResult {
  openingBalance?: number;
  incomeTotal?: number;
  expenseTotal?: number;
  endingBalance?: number;
  current?: number;
  pageSize?: number;
  total?: number;
  records?: IncomeExpenseRecord[];
}

export interface IncomeExpenseItem {
  id?: number;
  name?: string;
  itemType?: 'income' | 'expense';
  participatePerformance?: boolean;
  disabled?: boolean;
}

export interface ManualIncomeExpenseRecord {
  settleId?: number | string;
  cashierId?: number | string;
  businessDate?: string;
  summary?: string;
  counterparty?: string;
  fundItem?: string;
  income?: number;
  expense?: number;
}

export function listIncomeExpenseRecords(data: IncomeExpenseRecordQuery) {
  return axios.post<IncomeExpenseRecordResult>(
    'api/user/appIncomeExpenseRecord/list',
    data
  );
}

export function addIncomeExpenseRecord(data: ManualIncomeExpenseRecord) {
  return axios.post<string>('api/user/appIncomeExpenseRecord/add', data);
}

export function listIncomeExpenseItems(data?: {
  itemType?: 'income' | 'expense';
  enabledOnly?: boolean;
}) {
  return axios.post<IncomeExpenseItem[]>(
    'api/user/appIncomeExpenseRecord/itemList',
    data || {}
  );
}

export function addIncomeExpenseItem(data: IncomeExpenseItem) {
  return axios.post('api/user/appIncomeExpenseRecord/itemAdd', data);
}

export function editIncomeExpenseItem(data: IncomeExpenseItem) {
  return axios.post('api/user/appIncomeExpenseRecord/itemEdit', data);
}

export function removeIncomeExpenseItem(id: number) {
  return axios.post('api/user/appIncomeExpenseRecord/itemRemove', { id });
}
