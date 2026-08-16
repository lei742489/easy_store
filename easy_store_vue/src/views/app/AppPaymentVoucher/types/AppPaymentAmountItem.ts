import { AppPurchaseOrder } from '@/views/app/AppPurchaseOrder/types/AppPurchaseOrder';

export interface AppPaymentAmountItem extends AppPurchaseOrder {
  phOrderId?: number;
  amount?: number;
}
