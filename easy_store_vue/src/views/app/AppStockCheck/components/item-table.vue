<template>
  <div class="item-form">
    <a-table
      ref="tableRef"
      :columns="columns"
      :data="data"
      :pagination="false"
      size="small"
      :scroll="{ x: 1120, y: 410 }"
      :scrollbar="true"
      :summary="true"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>
      <template #goods="{ rowIndex }">
        <a-auto-complete
          v-model="data[rowIndex].goodsId_dictText"
          :data="goodsSearchData"
          :allow-clear="true"
          :filter-option="() => true"
          placeholder=""
          @search="handleSearch"
          @select="(value: string) => selectGoods(data[rowIndex], value)"
        />
      </template>
      <template #bookQuantity="{ rowIndex }">
        {{ formatQuantity(data[rowIndex].bookQuantity) }}
      </template>
      <template #actualQuantity="{ rowIndex }">
        <a-input-number
          v-model="data[rowIndex].actualQuantity"
          :min="0"
          :precision="0"
          @change="updateItem(data[rowIndex])"
        />
      </template>
      <template #profitLossQuantity="{ rowIndex }">
        <span :class="quantityClass(data[rowIndex].profitLossQuantity)">
          {{ formatQuantity(data[rowIndex].profitLossQuantity) }}
        </span>
      </template>
      <template #unitPrice="{ rowIndex }">
        {{ `¥${formatPrice(data[rowIndex].unitPrice || 0)}` }}
      </template>
      <template #profitLossAmount="{ rowIndex }">
        <span :class="amountClass(data[rowIndex].profitLossAmount)">
          {{ `¥${formatPrice(data[rowIndex].profitLossAmount || 0)}` }}
        </span>
      </template>
      <template #note="{ rowIndex }">
        <a-input v-model="data[rowIndex].note" />
      </template>
      <template #operations="{ rowIndex }">
        <a-popconfirm content="确认删除该商品？" @ok="removeItem(rowIndex)">
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>
      <template #summary-cell="{ column }">
        <template v-if="column.dataIndex === 'index'">
          <a-button
            type="text"
            size="small"
            style="padding: 0"
            @click="addItem"
          >
            增加一行
          </a-button>
        </template>
        <template v-else-if="column.dataIndex === 'goodsId'">
          <div class="summary-label">合计</div>
        </template>
        <template v-else-if="column.dataIndex === 'profitLossQuantity'">
          <span :class="quantityClass(totals.quantity)">
            {{ formatQuantity(totals.quantity) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'profitLossAmount'">
          <span :class="amountClass(totals.amount)">
            {{ `¥${formatPrice(totals.amount)}` }}
          </span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { formatPrice } from '@/api/common';
  import { searchKey } from '@/views/app/goods/api/api-AppGoods';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import type { AppStockCheckItem } from '../types/AppStockCheckItem';

  const tableRef = ref();
  const data = ref<AppStockCheckItem[]>([]);
  const goodsSearchData = ref<GoodsSearchResult[]>([]);
  const emit = defineEmits<{
    (event: 'change'): void;
  }>();

  const columns: TableColumnData[] = [
    {
      title: '行号',
      dataIndex: 'index',
      slotName: 'index',
      width: 64,
      align: 'center',
    },
    {
      title: '品名规格',
      dataIndex: 'goodsId',
      slotName: 'goods',
      minWidth: 300,
      align: 'left',
    },
    {
      title: '单位',
      dataIndex: 'unit',
      width: 80,
      align: 'center',
    },
    {
      title: '账存数量',
      dataIndex: 'bookQuantity',
      slotName: 'bookQuantity',
      width: 120,
      align: 'right',
    },
    {
      title: '实际数量',
      dataIndex: 'actualQuantity',
      slotName: 'actualQuantity',
      width: 120,
      align: 'right',
    },
    {
      title: '盈亏数量',
      dataIndex: 'profitLossQuantity',
      slotName: 'profitLossQuantity',
      width: 120,
      align: 'right',
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      slotName: 'unitPrice',
      width: 120,
      align: 'right',
    },
    {
      title: '盈亏金额',
      dataIndex: 'profitLossAmount',
      slotName: 'profitLossAmount',
      width: 130,
      align: 'right',
    },
    {
      title: '备注',
      dataIndex: 'note',
      slotName: 'note',
      minWidth: 150,
      align: 'left',
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 82,
      align: 'center',
    },
  ];

  const totals = computed(() =>
    data.value.reduce(
      (summary, item) => ({
        quantity: summary.quantity + Number(item.profitLossQuantity || 0),
        amount: summary.amount + Number(item.profitLossAmount || 0),
      }),
      { quantity: 0, amount: 0 }
    )
  );

  const createItem = (): AppStockCheckItem => ({
    goodsId: undefined,
    goodsId_dictText: undefined,
    unit: undefined,
    bookQuantity: undefined,
    actualQuantity: undefined,
    profitLossQuantity: undefined,
    unitPrice: undefined,
    profitLossAmount: undefined,
    note: undefined,
  });

  const emitChange = () => emit('change');
  const formatQuantity = (value?: number) => Number(value || 0).toFixed(2);
  const quantityClass = (value?: number) =>
    Number(value || 0) < 0 ? 'negative-value' : 'positive-value';
  const amountClass = (value?: number) =>
    Number(value || 0) < 0 ? 'negative-value' : 'positive-value';

  const addItem = () => {
    data.value.push(createItem());
    nextTick(() => {
      const body = tableRef.value?.$el?.querySelector('.arco-table-body');
      if (body) body.scrollTop = body.scrollHeight;
    });
  };

  const initializeRows = (count: number) => {
    data.value = [];
    for (let index = 0; index < count; index += 1) {
      data.value.push(createItem());
    }
  };

  const handleSearch = async (key: string) => {
    if (!key) {
      goodsSearchData.value = [];
      return;
    }
    const { data: result } = await searchKey(key, 1);
    goodsSearchData.value = result || [];
  };

  function updateItem(item: AppStockCheckItem) {
    item.profitLossQuantity =
      Number(item.actualQuantity || 0) - Number(item.bookQuantity || 0);
    item.profitLossAmount =
      Number(item.profitLossQuantity || 0) * Number(item.unitPrice || 0);
    emitChange();
  }

  const selectGoods = (item: AppStockCheckItem, value: string) => {
    const goods = goodsSearchData.value.find(
      (option) => option.value === value
    );
    if (!goods || goods.goodsId === undefined) return;
    item.goodsId = goods.goodsId;
    item.goodsId_dictText = goods.value;
    item.goodsName = goods.value;
    item.unit = goods.unit;
    item.bookQuantity = Number(goods.stock || 0);
    item.actualQuantity = Number(goods.stock || 0);
    item.unitPrice = Number(goods.costPrice || 0);
    updateItem(item);
  };

  const removeItem = (index: number) => {
    data.value.splice(index, 1);
    if (!data.value.length) initializeRows(15);
    emitChange();
  };

  const initData = (items: AppStockCheckItem[]) => {
    if (items?.length) {
      data.value = items.map((item) => ({
        ...item,
        goodsId_dictText: item.goodsId_dictText || item.goodsName,
      }));
      return;
    }
    initializeRows(15);
  };

  const clearAll = () => {
    initializeRows(15);
    emitChange();
  };

  const getItemsList = () =>
    data.value
      .filter((item) => item.goodsId !== undefined && item.goodsId !== null)
      .map((item) => ({ ...item }));

  defineExpose({ clearAll, getItemsList, initData });
</script>

<style lang="less" scoped>
  .item-form {
    width: 100%;
    min-height: 450px;
  }

  .summary-label {
    font-weight: 600;
    text-align: right;
  }

  .positive-value {
    color: rgb(var(--green-6));
  }

  .negative-value {
    color: rgb(var(--red-6));
  }
</style>
