import { AppPaymentSettleItem } from '@/views/app/AppPaymentVoucher/types/AppPaymentSettleItem';
import { AppPaymentAmountItem } from '@/views/app/AppPaymentVoucher/types/AppPaymentAmountItem';

export interface AppPaymentVoucher {
  /** 主键 */
  id?: number;
  /** 订单号 */
  orderNo?: string;
  /** 供应商 */
  supplierId?: string;

  supplierId_dictText?: string;

  /** 营业员 */
  cashierId?: number;

  cashierId_dictText?: string;

  cashierName?: string;

  status?: number;

  /** 付款金额 */
  amount?: number;
  /** 备注 */
  note?: string;
  /** 建立时间 */
  createTime?: string | Date;

  settleItems?: AppPaymentSettleItem[];

  amountItems?: AppPaymentAmountItem[];

  searchKey?: string;

  createTime_begin?: string;

  createTime_end?: string;

  selections?: string;
}
