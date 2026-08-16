export interface categoryType {
  id?: number;
  key?: number;
  title?: string;
  parentId?: number;
  createTime?: Date;
  disabled?: boolean;
  children?: categoryType[];
}
