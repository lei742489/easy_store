export interface AppPurchaseOrderItem {
  /** 主键 */
  id?: number;

  /** 货品名称 */
  goodsId?: number;

  goodsId_dictText?: string;

  /** 货品代码 */
  goodsCode?: string;

  goodsName?: string;

  /** 货品分类 */
  categoryId?: number;
  categoryId_dictText?: string;

  /** 单位 */
  unit?: string;

  /** 数量 */
  quantity?: number;

  /** 单价 */
  unitPrice?: number;

  /** 总价 */
  totalAmount?: number;

  totalAmountEdited?: boolean;

  /** 单号 */
  orderId?: number;

  stock?: number;

  /** 备注 */
  note?: string;

  /** 建立时间 */
  createTime?: string | Date;

  /** 修改时间 */
  updateTime?: string | Date;
}
