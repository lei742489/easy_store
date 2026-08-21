<template>
  <order-item-table
    ref="tableRef"
    goods-search-panel
    goods-search-type="purchase"
    v-model:order-type="orderType"
    @change="emit('change', $event)"
  />
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import OrderItemTable from '@/views/app/components/order-item-table.vue';
  import type { AppPurchaseOrderItem } from '../types/AppPurchaseOrderItem';

  const orderType = defineModel<number>('orderType');
  const emit = defineEmits<{
    (e: 'change', changeDate: number): void;
  }>();

  const tableRef = ref<InstanceType<typeof OrderItemTable> | null>(null);

  const getItemsList = () => {
    return (tableRef.value?.getItemsList() || []) as AppPurchaseOrderItem[];
  };

  const initData = async (itemList: AppPurchaseOrderItem[]) => {
    await tableRef.value?.initData(itemList);
  };

  const clearAll = () => {
    tableRef.value?.clearAll();
  };

  defineExpose({ getItemsList, initData, clearAll });
</script>
