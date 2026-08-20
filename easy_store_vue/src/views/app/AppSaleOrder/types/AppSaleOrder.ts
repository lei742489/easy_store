import { AppSaleOrderItem } from '@/views/app/AppSaleOrder/types/AppSaleOrderItem';

export interface AppSaleOrder {
  /** 主键 */
  id?: number;

  /** 单号 */
  orderNo?: string;

  /** 供应商 */
  customerId?: number | string;

  customerId_dictText?: string;

  /** 订单类型：1进货，2退货 */
  orderType?: number;

  /** 结算账户 */
  settleId?: number;

  settleId_dictText?: string;

  /** 收银员 */
  cashierId?: number;

  cashierId_dictText?: string;

  cashierName?: string;

  status?: number;

  /** 应付金额 */
  payableAmount?: number;

  /** 总金额 */
  totalAmount?: number;

  /** 实付金额 */
  paidAmount?: number;

  /** 未付金额 */
  unpaidAmount?: number;

  grossProfit?: number;

  /** 运费 */
  freightAmount?: number;

  /** 折后金额 */
  discountedAmount?: number;

  /** 折扣率 */
  discountRate?: number;

  /** 备注 */
  note?: string;

  /** 建立时间 */
  createTime?: string | Date;

  /** 修改时间 */
  updateTime?: string | Date;

  updateBy?: string;

  items?: AppSaleOrderItem[];

  searchKey?: string;

  createTime_begin?: string;

  createTime_end?: string;

  selections?: string;
}
