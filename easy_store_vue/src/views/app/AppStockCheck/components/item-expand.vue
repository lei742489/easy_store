<template>
  <a-table
    :columns="columns"
    :data="items || []"
    :pagination="false"
    :bordered="{ cell: false }"
    size="small"
  />
</template>

<script lang="ts" setup>
  import { h } from 'vue';
  import { formatPrice } from '@/api/common';
  import type { AppStockCheckItem } from '../types/AppStockCheckItem';

  const items = defineModel<AppStockCheckItem[]>('items');

  const columns = [
    { title: '品名规格', dataIndex: 'goodsId_dictText', minWidth: 260 },
    { title: '单位', dataIndex: 'unit', width: 80, align: 'center' },
    {
      title: '账存数量',
      dataIndex: 'bookQuantity',
      width: 110,
      align: 'right',
      render: (record: any) =>
        h('span', null, Number(record.record.bookQuantity || 0).toFixed(2)),
    },
    {
      title: '实际数量',
      dataIndex: 'actualQuantity',
      width: 110,
      align: 'right',
      render: (record: any) =>
        h('span', null, Number(record.record.actualQuantity || 0).toFixed(2)),
    },
    {
      title: '盈亏数量',
      dataIndex: 'profitLossQuantity',
      width: 110,
      align: 'right',
      render: (record: any) =>
        h(
          'span',
          {
            class:
              Number(record.record.profitLossQuantity || 0) < 0
                ? 'negative-value'
                : 'positive-value',
          },
          Number(record.record.profitLossQuantity || 0).toFixed(2)
        ),
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 110,
      align: 'right',
      render: (record: any) =>
        h(
          'span',
          null,
          `¥${formatPrice(Number(record.record.unitPrice || 0))}`
        ),
    },
    {
      title: '盈亏金额',
      dataIndex: 'profitLossAmount',
      width: 120,
      align: 'right',
      render: (record: any) =>
        h(
          'span',
          {
            class:
              Number(record.record.profitLossAmount || 0) < 0
                ? 'negative-value'
                : 'positive-value',
          },
          `¥${formatPrice(Number(record.record.profitLossAmount || 0))}`
        ),
    },
    { title: '备注', dataIndex: 'note', minWidth: 160 },
  ];
</script>

<style lang="less" scoped>
  :deep(.positive-value) {
    color: rgb(var(--green-6));
  }

  :deep(.negative-value) {
    color: rgb(var(--red-6));
  }
</style>
