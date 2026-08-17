import axios from 'axios';
import type { RouteRecordNormalized } from 'vue-router';
import { UserState } from '@/store/modules/user/types';
import { BasicInfoModel } from '@/api/user-center';

export interface LoginData {
  username: string;
  password: string;
}

export interface RegData {
  username: string;
  password: string;
  captcha: string;
}

export interface LoginRes {
  token: string;
}

export function login(data: LoginData) {
  return axios.post<LoginRes>('/api/login', data);
}

export function register(data: RegData) {
  return axios.post<LoginRes>('/api/register', data);
}

export function logout() {
  return axios.post<LoginRes>('/api/user/logout', {});
}

export function getUserInfo() {
  return axios.post<UserState>('/api/user/getInfo', {});
}

export function edit(data: BasicInfoModel) {
  return axios.post<any>('/api/user/edit', data);
}

export function updatePwd(data: BasicInfoModel) {
  return axios.post<any>('/api/user/updatePwd', data);
}

export function resetSystemData(pwd: string) {
  return axios.post<any>('/api/user/system/resetData', { pwd });
}

export function verifySystemResetPassword(pwd: string) {
  return axios.post<any>('/api/user/system/verifyResetDataPassword', { pwd });
}

export function getMenuList() {
  return axios.post<RouteRecordNormalized[]>('/api/user/menu');
}
