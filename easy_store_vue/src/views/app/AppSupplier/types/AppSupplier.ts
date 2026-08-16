export interface AppSupplier {
  /** 主键 */
  id?: number;

  /** 客户名称 */
  name?: string;

  pyCode?: string;

  /** 联系人 */
  contactName?: string;

  /** 手机 */
  mobile?: string;

  /** 电话 */
  phone?: string;

  /** 邮件 */
  mail?: string;

  /** 邮编 */
  postal?: string;

  /** 详细地址 */
  address?: string;

  /** QQ号 */
  qq?: string;

  /** 备注 */
  note?: string;

  /** 状态 */
  status?: number;

  /** 初期应付款 */
  defPayable?: number;

  /** 应付款 */
  payable?: number;

  /** 建立时间 */
  createTime?: string | Date;
}
