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
  import { onMounted, ref } from 'vue';
  import { list } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';

  const dataList = ref<AppSupplier[]>([]);
  const supplierId = defineModel<number | string>('supplierId');
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
    if (supplierId.value !== undefined && supplierId.value !== null) {
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
