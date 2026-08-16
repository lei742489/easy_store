export interface AppCustomerQuote {
  id?: number;
  customerId?: number | string;
  customerName?: string;
  customerPyCode?: string;
  goodsId?: number | string;
  goodsTitle?: string;
  goodsPyCode?: string;
  unit?: string;
  salePrc?: number;
  tradePrc?: number;
  quotePrice?: number;
  note?: string;
  createTime?: string | Date;
  updateTime?: string | Date;
  customerKey?: string;
  goodsKey?: string;
  selections?: string;
}
