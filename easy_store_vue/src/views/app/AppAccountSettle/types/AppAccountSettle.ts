export interface AppAccountSettle {
  /** 主键 */
  id?: number;

  /** 帐户名称 */
  name?: string;

  /** 帐户分类 */
  typeId?: number;

  typeId_dictText?: number;

  /** 银行名称 */
  bankName?: string;

  /** 银行卡号 */
  bankCard?: string;

  /** 初始余额 */
  initPrc?: number;

  /** 当前余额 */
  curPrc?: number;

  /** 备注 */
  note?: string;
}

export interface AppAccountSettleType {
  /** 主键 */
  id?: number;
  /** 名称 */
  name?: string;
}
