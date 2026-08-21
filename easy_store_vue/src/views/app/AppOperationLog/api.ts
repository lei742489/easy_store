import axios from 'axios';

export interface OperationLogQuery {
  operatorName?: string;
  menuName?: string;
  operationType?: string;
  clientIp?: string;
  startDate?: string;
  endDate?: string;
  current?: number;
  pageSize?: number;
}

export interface OperationLogRecord {
  id?: number;
  operatorId?: string;
  operatorName?: string;
  menuName?: string;
  operationType?: string;
  requestUri?: string;
  clientIp?: string;
  operateTime?: string;
  dataJson?: string;
}

export interface OperationLogPage {
  records: OperationLogRecord[];
  total: number;
  current: number;
  pageSize: number;
}

export function listPage(data: OperationLogQuery) {
  return axios.post<OperationLogPage>(
    'api/user/appOperationLog/listPage',
    data
  );
}
