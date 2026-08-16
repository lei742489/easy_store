export interface Customer {
  id?: number;

  /** 客户名称 */
  name?: string;

  /** 联系人 */
  contactName?: string;

  pyCode?: string;

  /** 手机 */
  mobile?: string;

  /** 电话 */
  phone?: string;

  /** 邮件 */
  mail?: string;

  /** 邮编 */
  postal?: string;

  /** 生日 */
  birthday?: string;

  /** 详细地址 */
  address?: string;

  /** QQ号 */
  qq?: string;

  /** 备注 */
  note?: string;

  /** 分类ID */
  categoryId?: number;

  categoryId_dictText?: string;

  /** 状态 */
  status?: number;

  /** 等级分类 */
  levelId?: number;

  levelId_dictText?: string;

  /** 折扣 */
  discount?: number;

  /** 初期欠款 */
  defPayable?: number;

  /** 当前欠款 */
  payable?: number;

  /** 建立时间 */
  createTime?: string | Date;
}
