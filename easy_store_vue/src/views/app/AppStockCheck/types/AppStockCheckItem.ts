export interface AppStockCheckItem {
  id?: number;
  goodsId?: number | string;
  goodsId_dictText?: string;
  goodsName?: string;
  unit?: string;
  bookQuantity?: number;
  actualQuantity?: number;
  profitLossQuantity?: number;
  unitPrice?: number;
  profitLossAmount?: number;
  checkId?: number;
  note?: string;
  createTime?: string | Date;
  updateTime?: string | Date;
}
