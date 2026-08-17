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
  import { onMounted, ref, watch } from 'vue';
  import { list } from '../api/api-customer';
  import { Customer } from '../types/customer';

  type SelectCustomer = Omit<Customer, 'id'> & {
    id?: number | string;
  };

  const dataList = ref<SelectCustomer[]>([]);
  const customerId = defineModel<number | string>('customerId');
  const loading = ref(false);

  const normalizeOptions = (items: Customer[] = []): SelectCustomer[] =>
    items.map((item) => ({
      ...item,
      id: item.id === undefined || item.id === null ? item.id : String(item.id),
    }));

  const syncSelectedValue = () => {
    if (customerId.value === undefined || customerId.value === null) return;
    const selected = dataList.value.find(
      (item) => String(item.id) === String(customerId.value)
    );
    if (selected?.id !== undefined) {
      customerId.value = selected.id;
    }
  };

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    try {
      const res = await list();
      dataList.value = normalizeOptions(res.data);
      syncSelectedValue();
    } finally {
      loading.value = false;
    }
  };

  const handleSearch = async (e: any) => {
    const res = await list(e);
    dataList.value = normalizeOptions(res.data);
    syncSelectedValue();
  };

  onMounted(() => {
    if (customerId.value !== undefined && customerId.value !== null) {
      fetchList();
    }
  });

  watch(customerId, (value) => {
    if (value !== undefined && value !== null) {
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
