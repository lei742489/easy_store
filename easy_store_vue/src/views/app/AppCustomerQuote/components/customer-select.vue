<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="customerId"
      :options="dataList"
      :placeholder="placeholder"
      :loading="loading"
      :allow-search="true"
      :field-names="{ value: 'id', label: 'name' }"
      :filter-option="() => true"
      :fallback-option="fallback"
      @search="handleSearch"
      @change="handleChange"
    />
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { list as getCustomerList } from '@/views/app/customer/api/api-customer';
  import type { Customer } from '@/views/app/customer/types/customer';

  const dataList = ref<Customer[]>([]);
  const customerId = defineModel<number | string>('customerId');
  const customerText = defineModel<string>('customerText');
  const loading = ref(false);

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    const res = await getCustomerList();
    dataList.value = res.data || [];
    loading.value = false;
  };

  const handleSearch = async (key: string) => {
    const res = await getCustomerList(key);
    dataList.value = res.data || [];
  };

  const handleChange = (
    value:
      | number
      | string
      | boolean
      | Record<string, any>
      | (number | string | boolean | Record<string, any>)[]
  ) => {
    const hit = dataList.value.find((item) => `${item.id}` === `${value}`);
    customerText.value = hit?.name || '';
  };

  const fallback = (value: any) => ({
    id: value,
    name: customerText.value || '',
  });

  defineProps<{
    placeholder?: string;
  }>();
</script>

<style lang="less" scoped>
  .su-search {
    width: 100%;
  }
</style>
