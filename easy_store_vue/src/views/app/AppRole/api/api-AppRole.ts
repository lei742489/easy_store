import axios from 'axios';
import { PolicyParams } from '@/api/list';
import { pageRecords } from '@/store/modules/app/types';
import type {
  AppHomeMenuGroup,
  AppPermissionTreeNode,
  AppRole,
} from '../types/AppRole';

export function add(data: AppRole) {
  return axios.post<any>('api/user/appRole/add', data);
}

export function edit(data: AppRole) {
  return axios.post<any>('api/user/appRole/edit', data);
}

export function remove(data: AppRole) {
  return axios.post<any>('api/user/appRole/remove', data);
}

export function listPage(param: PolicyParams) {
  return axios.post<pageRecords>('api/user/appRole/listPage', param);
}

export function list() {
  return axios.post<AppRole[]>('api/user/appRole/list', {});
}

export function menuIds(roleId?: number) {
  return axios.post<number[]>('api/user/appRole/menuIds', { roleId });
}

export function permissionCodes(roleId?: number) {
  return axios.post<string[]>('api/user/appRole/permissionCodes', { roleId });
}

export function listAllMenus() {
  return axios.post<AppPermissionTreeNode[]>(
    'api/user/appHomeMenu/listAll',
    {}
  );
}

export function listHomeMenus() {
  return axios.post<AppHomeMenuGroup[]>('api/user/appHomeMenu/listHome', {});
}

export function pendingApproveCounts() {
  return axios.post<Record<string, number>>(
    'api/user/appHomeMenu/pendingApproveCounts',
    {}
  );
}
