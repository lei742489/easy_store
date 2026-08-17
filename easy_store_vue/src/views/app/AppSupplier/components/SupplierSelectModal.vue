<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="supplierId"
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
  import { list } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';

  type SelectSupplier = Omit<AppSupplier, 'id'> & {
    id?: number | string;
  };

  const dataList = ref<SelectSupplier[]>([]);
  const supplierId = defineModel<number | string>('supplierId');
  const loading = ref(false);

  const normalizeOptions = (items: AppSupplier[] = []): SelectSupplier[] =>
    items.map((item) => ({
      ...item,
      id: item.id === undefined || item.id === null ? item.id : String(item.id),
    }));

  const syncSelectedValue = () => {
    if (supplierId.value === undefined || supplierId.value === null) return;
    const selected = dataList.value.find(
      (item) => String(item.id) === String(supplierId.value)
    );
    if (selected?.id !== undefined) {
      supplierId.value = selected.id;
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
    if (supplierId.value !== undefined && supplierId.value !== null) {
      fetchList();
    }
  });

  watch(supplierId, (value) => {
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
