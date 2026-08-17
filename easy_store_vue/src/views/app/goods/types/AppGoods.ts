export interface AppGoods {
  /** 主键 */
  id?: number;

  /** 货品名称 */
  title?: string;

  /** 供应商货品名 */
  supplierTitle?: string;

  /** 货品分类 */
  categoryId?: number;

  categoryId_dictText?: string;

  /** 货品代码 */
  goodsCode?: string;

  /** 缩略图 */
  imgUrl?: string;

  /** 初始成本 */
  initCost?: number;

  /** 初始库存 */
  initStock?: number;

  /** 当前库存 */
  stock?: number;

  stockCost?: number;

  costPrice?: number;

  /** 单位 */
  unit?: string;

  /** 零售价 */
  salePrc?: number;

  /** 批发价 */
  tradePrc?: number;

  /** 进货价 */
  purPrc?: number;

  /** 最大库存 */
  maxStock?: number;

  /** 最小库存 */
  minStock?: number;

  /** 供应商 */
  supplierId?: number;

  supplierId_dictText?: string;

  /** 备注 */
  note?: string;

  /** 状态 */
  status?: number;

  zeroStock?: boolean;

  /** 建立时间 */
  createTime?: string | Date;

  /** 修改时间 */
  updateTime?: string | Date;

  selections?: string;
}

export interface AppUnit {
  id?: number;
  name?: string;
}
