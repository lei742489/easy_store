<template>
  <div class="quote-items-table">
    <a-table
      row-key="rowId"
      :columns="columns"
      :data="data"
      :pagination="false"
      :bordered="{ cell: true }"
      :scrollbar="true"
      :summary="true"
      size="small"
      :scroll="{ x: '100%', y: 260 }"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>

      <template #target="{ rowIndex }">
        <goods-select
          v-if="props.mode === 'customer'"
          v-model:goods-id="data[rowIndex].goodsId"
          v-model:goods-text="data[rowIndex].goodsTitle"
          placeholder="请选择商品 / 拼音首字母"
          @change="(goods) => handleGoodsChange(rowIndex, goods)"
        />
        <customer-select
          v-else
          v-model:customer-id="data[rowIndex].customerId"
          v-model:customer-text="data[rowIndex].customerName"
          placeholder="请选择客户 / 拼音首字母"
        />
      </template>

      <template #unit="{ rowIndex }">
        <a-input v-model="data[rowIndex].unit" placeholder="单位" />
      </template>

      <template #salePrc="{ record }">
        {{ formatAmount(record.salePrc) }}
      </template>

      <template #tradePrc="{ record }">
        {{ formatAmount(record.tradePrc) }}
      </template>

      <template #quotePrice="{ rowIndex }">
        <a-input-number
          v-model="data[rowIndex].quotePrice"
          :precision="2"
          :min="0"
          placeholder="大客户价"
        />
      </template>

      <template #note="{ rowIndex }">
        <a-input v-model="data[rowIndex].note" placeholder="备注" />
      </template>

      <template #operations="{ rowIndex }">
        <a-popconfirm content="确认删除该行?" @ok="handleRemove(rowIndex)">
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>

      <template #summary-cell="{ column }">
        <div v-if="column.dataIndex === 'index'">
          <a-button
            type="text"
            size="small"
            style="padding: 0"
            @click="addItem(true)"
          >
            增加一行
          </a-button>
        </div>
        <div v-if="column.dataIndex === 'operations'">
          <a-popconfirm content="确认清空所有行?" @ok="clearAll">
            <a-button type="text" size="small">清空</a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { formatPrice } from '@/api/common';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import type { AppCustomerQuote } from '../types/AppCustomerQuote';
  import CustomerSelect from './customer-select.vue';
  import GoodsSelect from './goods-select.vue';

  type QuoteMode = 'customer' | 'goods';
  type QuoteRow = AppCustomerQuote & { rowId?: string };

  const props = defineProps<{
    mode: QuoteMode;
  }>();

  const customerId = defineModel<number | string | undefined>('customerId');
  const customerText = defineModel<string | undefined>('customerText');
  const goodsId = defineModel<number | string | undefined>('goodsId');
  const goodsText = defineModel<string | undefined>('goodsText');

  const data = ref<QuoteRow[]>([]);
  let rowSeed = 0;

  const baseColumns: TableColumnData[] = [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 70,
    },
    {
      title: props.mode === 'customer' ? '商品' : '客户',
      dataIndex: 'target',
      slotName: 'target',
      align: 'center',
      width: 300,
    },
  ];

  const priceColumns: TableColumnData[] = [
    {
      title: '大客户价',
      dataIndex: 'quotePrice',
      slotName: 'quotePrice',
      align: 'center',
      width: 160,
    },
    {
      title: '备注',
      dataIndex: 'note',
      slotName: 'note',
      align: 'center',
      width: 180,
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 100,
    },
  ];

  const columns = computed<TableColumnData[]>(() => {
    if (props.mode !== 'customer') {
      return [...baseColumns, ...priceColumns];
    }
    return [
      ...baseColumns,
      {
        title: '单位',
        dataIndex: 'unit',
        slotName: 'unit',
        align: 'center',
        width: 120,
      },
      {
        title: '零售价',
        dataIndex: 'salePrc',
        slotName: 'salePrc',
        align: 'center',
        width: 120,
      },
      {
        title: '批发价',
        dataIndex: 'tradePrc',
        slotName: 'tradePrc',
        align: 'center',
        width: 120,
      },
      ...priceColumns,
    ];
  });

  const makeRowId = () => {
    rowSeed += 1;
    return `${Date.now()}-${rowSeed}`;
  };

  const makeRow = (item: AppCustomerQuote = {}): QuoteRow => ({
    ...item,
    rowId: makeRowId(),
  });

  const addItem = (scroll = false) => {
    data.value.push(makeRow());
    if (scroll) {
      nextTick(() => {
        const tableEl = document.querySelector(
          '.quote-items-table .arco-table-body'
        );
        if (tableEl) {
          tableEl.scrollTop = tableEl.scrollHeight;
        }
      });
    }
  };

  const initBlankRows = () => {
    data.value = [];
    for (let i = 0; i < 6; i += 1) {
      addItem();
    }
  };

  const handleGoodsChange = (rowIndex: number, goods?: GoodsSearchResult) => {
    if (!goods) {
      return;
    }
    data.value[rowIndex].unit = goods.unit;
    data.value[rowIndex].salePrc = goods.salePrc;
    data.value[rowIndex].tradePrc = goods.tradePrc;
  };

  const handleRemove = (rowIndex: number) => {
    data.value.splice(rowIndex, 1);
    if (data.value.length === 0) {
      addItem();
    }
  };

  const clearAll = () => {
    initBlankRows();
  };

  const isBlankRow = (row: QuoteRow) => {
    const targetId = props.mode === 'customer' ? row.goodsId : row.customerId;
    return !targetId && row.quotePrice == null && !row.note;
  };

  const getFixedCustomerId = () =>
    props.mode === 'customer' ? customerId.value : undefined;

  const getFixedGoodsId = () =>
    props.mode === 'goods' ? goodsId.value : undefined;

  const getItemsList = () => {
    const map = new Map<string, AppCustomerQuote>();
    data.value.forEach((row) => {
      if (isBlankRow(row)) {
        return;
      }

      const targetId = props.mode === 'customer' ? row.goodsId : row.customerId;
      const fixedCustomerId = getFixedCustomerId();
      const fixedGoodsId = getFixedGoodsId();
      if (
        (props.mode === 'customer' && !fixedCustomerId) ||
        (props.mode === 'goods' && !fixedGoodsId) ||
        !targetId
      ) {
        Message.error('报价明细未填写完整');
        throw new Error('quote item invalid');
      }

      if (row.quotePrice == null) {
        Message.error('请填写大客户价');
        throw new Error('quote price required');
      }

      const item: AppCustomerQuote =
        props.mode === 'customer'
          ? {
              id: row.id,
              customerId: fixedCustomerId,
              customerName: customerText.value,
              goodsId: row.goodsId,
              goodsTitle: row.goodsTitle,
              unit: row.unit,
              salePrc: row.salePrc,
              tradePrc: row.tradePrc,
              quotePrice: row.quotePrice,
              note: row.note,
            }
          : {
              id: row.id,
              customerId: row.customerId,
              customerName: row.customerName,
              goodsId: fixedGoodsId,
              goodsTitle: goodsText.value,
              quotePrice: row.quotePrice,
              note: row.note,
            };
      map.set(`${item.customerId}_${item.goodsId}`, item);
    });
    return Array.from(map.values());
  };

  const initData = (list: AppCustomerQuote[] = []) => {
    if (list.length === 0) {
      initBlankRows();
      return;
    }
    data.value = list.map((item) => makeRow(item));
  };

  const formatAmount = (value?: number) =>
    value == null ? '' : `￥${formatPrice(value)}`;

  initBlankRows();

  defineExpose({ getItemsList, initData, clearAll });
</script>

<style lang="less" scoped>
  .quote-items-table {
    width: 100%;
    min-height: 260px;
  }
</style>
