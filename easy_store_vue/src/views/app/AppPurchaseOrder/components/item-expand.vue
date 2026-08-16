<template>
  <div class="item-expand">
    <a-table
      :columns="columns"
      :data="data"
      :pagination="false"
      :bordered="{ cell: false }"
      :summary="true"
      size="small"
      :stripe="true"
    >
      <template #index>
        <div
          style="
            height: 40px;
            display: flex;
            align-items: center;
            justify-content: center;
          "
        >
          #
        </div>
      </template>
      <template #summary-cell="{ column, record }">
        <div
          v-if="column.dataIndex == 'categoryId_dictText'"
          style="color: #5941cd"
        >
          <div style="width: 100%; text-align: center"
            ><a-link>合计:</a-link></div
          >
        </div>
        <div
          v-if="
            column.dataIndex === 'quantity' ||
            column.dataIndex === 'totalAmount'
          "
          ><a-link
            >{{ column.dataIndex === 'totalAmount' ? '￥' : '' }}
            {{ formatPrice(record[column.dataIndex]) }}</a-link
          ></div
        >
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { AppPurchaseOrderItem } from '@/views/app/AppPurchaseOrder/types/AppPurchaseOrderItem';
  import { formatPrice, getPriceStrByH } from '@/api/common';

  const data = defineModel<AppPurchaseOrderItem[]>('itemList');
  const columns = [
    {
      title: '#',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 50,
    },
    {
      title: '货品名称',
      dataIndex: 'goodsId_dictText',
      align: 'center',
      width: 300,
    },
    {
      title: '单位',
      dataIndex: 'unit',
      align: 'center',
      slotName: 'unit',
    },
    {
      title: '货品类别',
      dataIndex: 'categoryId_dictText',
      align: 'center',
      slotName: 'category',
      width: 200,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      align: 'center',
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      align: 'center',
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      align: 'center',
      render: (record: any) => {
        const d = record.record;
        return getPriceStrByH((d as any).totalAmount);
      },
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      slotName: 'note',
    },
  ];
</script>

<style lang="less" scoped>
  .item-expand {
  }
</style>
