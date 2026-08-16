export interface AppGoodsCategory {
  id?: number;
  key?: number;
  pyCode?: string;
  title?: string;
  parentId?: number;
  createTime?: Date;
  disabled?: boolean;
  children?: AppGoodsCategory[];
}
