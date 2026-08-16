<template>
  <order-item-table
    ref="tableRef"
    v-model:order-type="orderType"
    @change="emit('change', $event)"
  />
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import OrderItemTable from '@/views/app/components/order-item-table.vue';
  import type { AppSaleOrderItem } from '../types/AppSaleOrderItem';

  const orderType = defineModel<number>('orderType');
  const emit = defineEmits<{
    (e: 'change', changeDate: number): void;
  }>();

  const tableRef = ref<InstanceType<typeof OrderItemTable> | null>(null);

  const getItemsList = () => {
    return (tableRef.value?.getItemsList() || []) as AppSaleOrderItem[];
  };

  const initData = async (itemList: AppSaleOrderItem[]) => {
    await tableRef.value?.initData(itemList);
  };

  const clearAll = () => {
    tableRef.value?.clearAll();
  };

  defineExpose({ getItemsList, initData, clearAll });
</script>
