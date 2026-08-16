import type { AppStockCheckItem } from './AppStockCheckItem';

export interface AppStockCheck {
  id?: number;
  orderNo?: string;
  cashierId?: number | string;
  cashierId_dictText?: string;
  cashierName?: string;
  profitLossQuantity?: number;
  profitLossAmount?: number;
  note?: string;
  createTime?: string | Date;
  updateTime?: string | Date;
  items?: AppStockCheckItem[];
  searchKey?: string;
  createTime_begin?: string;
  createTime_end?: string;
}
