import { AppSaleOrder } from '@/views/app/AppSaleOrder/types/AppSaleOrder';

export interface AppReceivePaymentAmountItem extends AppSaleOrder {
  phOrderId?: number;
  amount?: number;
}
