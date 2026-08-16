export interface AppReceivePaymentSettleItem {
  /** 主键 */
  id?: number;
  /** 帐户名称 */
  settleId?: string;

  settleId_dictText?: string;
  /** 备注 */
  note?: string;
  /** 金额 */
  amount?: number;
}
