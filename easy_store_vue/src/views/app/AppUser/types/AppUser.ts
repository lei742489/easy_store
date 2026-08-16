export interface AppUser {
  /** 主键 */
  id?: number;

  /** 登录账号 */
  userName?: string;

  /** 名称 */
  realName?: string;

  /** 登录密码 */
  password?: string;

  /** 备注 */
  remarks?: string;

  /** 手机号 */
  mobile?: string;

  /** 提成比例（百分比） */
  commissionRate?: number;

  isRoot?: number;

  roleId?: number;

  roleId_dictText?: string;

  status?: number;
}
