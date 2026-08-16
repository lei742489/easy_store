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
      @search="handleSearch"
    >
    </a-select>
  </div>
</template>

<script lang="ts" setup>
  import { onMounted, ref } from 'vue';
  import { list } from '../api/api-customer';
  import { Customer } from '../types/customer';

  const dataList = ref<Customer[]>([]);
  const customerId = defineModel<number | string>('customerId');
  const loading = ref(false);

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    const res = await list();
    dataList.value = res.data;
    loading.value = false;
  };

  const handleSearch = async (e: any) => {
    const res = await list(e);
    dataList.value = res.data;
  };

  onMounted(() => {
    if (customerId.value !== undefined && customerId.value !== null) {
      fetchList();
    }
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
