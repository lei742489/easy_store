export interface AppRole {
  id?: number;
  name?: string;
  remarks?: string;
  status?: number;
  menuIds?: number[];
  permissionCodes?: string[];
  createTime?: string | Date;
}

export interface AppHomeMenu {
  id?: number;
  code?: string;
  groupCode?: string;
  groupTitle?: string;
  name?: string;
  icon?: string;
  url?: string;
  action?: string;
  sortNo?: number;
  status?: number;
  rootOnly?: number;
}

export interface AppHomeMenuGroup {
  code: string;
  title: string;
  menus: AppHomeMenu[];
}

export interface AppPermissionTreeNode {
  key: string;
  title: string;
  menuId?: number;
  menuCode?: string;
  action?: 'view' | 'add' | 'edit' | 'remove' | 'audit';
  checkable?: boolean;
  children?: AppPermissionTreeNode[];
}
