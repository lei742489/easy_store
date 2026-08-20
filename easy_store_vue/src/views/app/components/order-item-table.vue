<template>
  <div class="item-form">
    <a-table
      ref="goodsTableRef"
      :columns="columns"
      :data="data"
      :pagination="false"
      size="small"
      :scroll="{ x: '100%', y: 258 }"
      :scrollbar="true"
      :summary="true"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>

      <template #goodsId="{ rowIndex }">
        <div style="display: flex; align-items: center; gap: 5px">
          <a-auto-complete
            v-model="data[rowIndex].goodsId_dictText"
            :data="goodsSearchData"
            :allow-clear="true"
            placeholder=""
            :filter-option="() => true"
            @select="(value:any) => selectGoods(data[rowIndex], value)"
            @search="handleSearchKey"
            @dropdown-reach-bottom="(value:any) => loadGoodsMore(data[rowIndex], value)"
          >
          </a-auto-complete>
        </div>
      </template>

      <template #unit="{ rowIndex }">
        <a-select
          v-model="data[rowIndex].unit"
          :options="unitList"
          :field-names="{ value: 'name', label: 'name' }"
          :allow-search="true"
          :allow-clear="true"
          placeholder=""
        >
        </a-select>
      </template>

      <template #category="{ rowIndex }">
        <category-select-tree
          v-model:category-id="data[rowIndex].categoryId"
          v-model:category-text="data[rowIndex].categoryId_dictText"
        ></category-select-tree>
      </template>

      <template #quantity="{ rowIndex }">
        <a-input-number
          v-if="orderType == 1"
          v-model="data[rowIndex].quantity"
          :min="1"
          @change="updateTotalAmount(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].quantity"
          style="color: red"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 1)"
        ></a-input-number>
      </template>

      <template #unitPrice="{ rowIndex }">
        <a-input-number
          v-model="data[rowIndex].unitPrice"
          :precision="2"
          :min="0"
          @change="updateTotalAmount(data[rowIndex])"
        ></a-input-number>
      </template>

      <template #totalAmount="{ rowIndex }">
        <a-input-number
          v-if="orderType == 1"
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          :min="0"
          @change="updateUnitPrice(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 2)"
        ></a-input-number>
      </template>

      <template #note="{ rowIndex }">
        <a-input v-model="data[rowIndex].note"></a-input>
      </template>

      <template #operations="{ rowIndex }">
        <a-popconfirm content="确认删除该条数据?" @ok="handelRemove(rowIndex)">
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>

      <template #summary-cell="{ column, record }">
        <div v-if="column.dataIndex === 'index'">
          <a-button
            type="text"
            size="medium"
            style="padding: 0"
            @click="addItem(1)"
            >增加一行</a-button
          >
        </div>
        <div v-if="column.dataIndex == 'categoryId'">
          <div style="width: 100%; text-align: right">合计:</div>
        </div>
        <div
          v-if="
            column.dataIndex === 'quantity' ||
            column.dataIndex === 'totalAmount'
          "
          >{{ column.dataIndex === 'totalAmount' ? '￥' : '' }}
          {{ formatPrice(record[column.dataIndex]) }}</div
        >

        <div v-if="column.dataIndex == 'operations'">
          <a-popconfirm content="清空所有数据?" @ok="clearAll">
            <a-button type="text" size="small">清空</a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import { ref, nextTick, watch } from 'vue';
  import { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import { getUnitList, searchKey } from '@/views/app/goods/api/api-AppGoods';
  import { mulPrice, formatPrice, divPrice, addPrice } from '@/api/common';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import type { AppUnit } from '@/views/app/goods/types/AppGoods';
  import CategorySelectTree from '@/views/app/goods/components/category-select-tree.vue';

  export interface OrderItemRow {
    id?: number;
    goodsId?: number;
    goodsId_dictText?: string;
    goodsName?: string;
    categoryId?: number;
    categoryId_dictText?: string;
    unit?: string;
    quantity?: number;
    unitPrice?: number;
    stock?: number;
    totalAmount?: number;
    orderId?: number;
    note?: string;
    createTime?: string | Date;
    updateTime?: string | Date;
  }

  const data = ref<OrderItemRow[]>([]);
  const goodsSearchData = ref<GoodsSearchResult[]>([]);
  const unitList = ref<AppUnit[]>([]);
  const orderType = defineModel<number>('orderType');
  const defaultItem: OrderItemRow = {
    id: undefined,
    goodsId: undefined,
    goodsId_dictText: undefined,
    categoryId: 0,
    unit: undefined,
    quantity: undefined,
    unitPrice: undefined,
    stock: undefined,
    totalAmount: undefined,
    note: undefined,
  };
  const goodsPageNo = ref(1);

  const columns: TableColumnData[] = [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 60,
    },
    {
      title: '货品名称',
      dataIndex: 'goodsId',
      align: 'center',
      width: 300,
      slotName: 'goodsId',
    },
    {
      title: '单位',
      dataIndex: 'unit',
      align: 'center',
      slotName: 'unit',
    },
    {
      title: '货品类别',
      dataIndex: 'categoryId',
      align: 'center',
      slotName: 'category',
      width: 200,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      align: 'center',
      slotName: 'quantity',
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      align: 'center',
      slotName: 'unitPrice',
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      align: 'center',
      slotName: 'totalAmount',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      slotName: 'note',
    },
    {
      title: '操作',
      align: 'center',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 100,
    },
  ];

  const emit = defineEmits<{
    (e: 'change', changeDate: number): void;
  }>();

  const goodsTableRef = ref();

  const scrollToBottom = () => {
    nextTick(() => {
      const wrapper =
        goodsTableRef.value?.$el?.querySelector('.arco-table-body');
      if (wrapper) {
        wrapper.scrollTop = wrapper.scrollHeight;
      }
    });
  };

  const addItem = (par?: number) => {
    const item: OrderItemRow = {};
    Object.assign(item, defaultItem);
    data.value.push(item);
    if (par !== undefined) scrollToBottom();
  };

  const fetchUnitData = async () => {
    if (unitList.value.length !== 0) return;
    unitList.value = (await getUnitList()).data;
  };

  const clearSearchData = () => {
    goodsSearchData.value = [];
    goodsPageNo.value = 1;
  };

  const handleSearchKey = async (key: any) => {
    if (!key) {
      clearSearchData();
      return;
    }
    goodsPageNo.value = 1;
    const res = await searchKey(key, 1);
    goodsSearchData.value = res.data;
  };

  const initItemList = (size: number) => {
    if (size <= 0) {
      const initNum = 10;
      for (let i = 0; i < initNum; i += 1) {
        addItem();
      }
    }
  };

  const initData = async (itemList: OrderItemRow[]) => {
    fetchUnitData();
    if (itemList.length > 0) {
      data.value = itemList;
      initItemList(itemList.length);
    } else {
      data.value = [];
      initItemList(0);
    }
    const res = await searchKey('', 1);
    goodsSearchData.value = res.data;
  };

  const loadGoodsMore = async (goodsItem: OrderItemRow) => {
    goodsPageNo.value += 1;
    const res = await searchKey(
      goodsItem.goodsId_dictText || '',
      goodsPageNo.value
    );
    if (res.data.length > 0) {
      goodsSearchData.value = goodsSearchData.value.concat(res.data);
    }
  };

  const emitChange = () => {
    let tList = 0;
    data.value.forEach((item) => {
      tList = addPrice(
        tList,
        mulPrice(item.quantity || 0, item.unitPrice || 0)
      );
    });
    emit('change', tList);
  };

  const updateTotalAmount = (goodsItem: OrderItemRow) => {
    goodsItem.totalAmount = mulPrice(
      goodsItem.quantity || 0,
      goodsItem.unitPrice || 0
    );
    emitChange();
  };

  const updateUnitPrice = (goodsItem: OrderItemRow) => {
    goodsItem.unitPrice = divPrice(
      goodsItem.totalAmount || 0,
      goodsItem.quantity || 0
    );
    emitChange();
  };

  const selectGoods = (goodsItem: OrderItemRow, value: any) => {
    const option = goodsSearchData.value.find((item) => item.value === value);
    if (option) {
      const {
        goodsId,
        unit,
        categoryId,
        categoryName,
        stock,
        value: goodsName,
      } = option;
      if (goodsId == null) return;
      goodsItem.goodsId = goodsId;
      goodsItem.goodsId_dictText = goodsName || option.label || '';
      goodsItem.goodsName = goodsItem.goodsId_dictText;
      goodsItem.unit = unit;
      goodsItem.categoryId = categoryId;
      goodsItem.quantity = orderType.value === 1 ? 1 : -1;
      goodsItem.categoryId_dictText = categoryName;
      goodsItem.stock = stock;
      goodsItem.unitPrice = undefined;
      goodsItem.totalAmount = undefined;
      emitChange();
    }
  };

  const handelRemove = (idx: number) => {
    data.value.splice(idx, 1);
    emitChange();
  };

  const getItemsList = () => {
    const resultList: OrderItemRow[] = [];
    data.value.forEach((item) => {
      const goodsName = (item.goodsId_dictText || '').trim();
      if (item.goodsId || goodsName) {
        resultList.push({
          ...item,
          goodsName,
        });
      }
    });
    return resultList;
  };

  const handleNegativeChange = (
    val: number | undefined,
    rowIndex: number,
    type: number
  ) => {
    const inputVal = val || 0;
    const negativeVal = inputVal > 0 ? -inputVal : inputVal;
    if (type === 1) {
      data.value[rowIndex].quantity = negativeVal;
      updateTotalAmount(data.value[rowIndex]);
    } else {
      data.value[rowIndex].totalAmount = negativeVal;
      updateUnitPrice(data.value[rowIndex]);
    }
  };

  const clearAll = () => {
    data.value = [];
    initItemList(0);
    emitChange();
  };

  watch(orderType, (newVal) => {
    data.value.forEach((item) => {
      if (item.goodsId !== undefined) {
        if (newVal === 2) {
          item.quantity = item.quantity ? -item.quantity : 0;
          item.totalAmount = item.totalAmount ? -item.totalAmount : 0;
        } else {
          item.quantity = Math.abs(item.quantity || 0);
          item.totalAmount = Math.abs(item.totalAmount || 0);
        }

        updateUnitPrice(item);
      }
    });
  });

  defineExpose({ getItemsList, initData, clearAll });
</script>

<style lang="less" scoped>
  .item-form {
    width: 100%;
    min-height: 240px;
  }
</style>
