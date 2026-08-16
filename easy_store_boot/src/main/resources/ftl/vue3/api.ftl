import axios from 'axios';
import { exportXls, PolicyParams, uploadFile } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type { ${className} } from '../types/${className}';

export function add(data: ${className}) {
    return axios.post<any>('api/user/${lowerName}/add', data);
}

export function edit(data: ${className}) {
    return axios.post<any>('api/user/${lowerName}/edit', data);
}

 export function remove(data: ${className}) {
    return axios.post<any>('api/user/${lowerName}/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/${lowerName}/listPage', param);
}

export function exportXlsFile(data: ${className}) {
  exportXls('api/user/${lowerName}/exportXls', data);
}

export function importExcel(file: File) {
 return uploadFile('api/user/${lowerName}/importExcel', file);
}