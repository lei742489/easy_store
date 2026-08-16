<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="settleId"
      :options="dataList"
      :placeholder="placeholder"
      :loading="loading"
      :field-names="{ value: 'id', label: 'name' }"
      :filter-option="() => true"
      :fallback-option="fallback"
    >
    </a-select>
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch } from 'vue';
  import { list } from '../api/api-AppAccountSettle';
  import { AppAccountSettle } from '../types/AppAccountSettle';

  const dataList = ref<AppAccountSettle[]>([]);
  const settleId = defineModel<string>('settleId');
  const settleText = defineModel<string>('settleText');
  const loading = ref(false);

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    const res = await list();
    dataList.value = res.data;
    loading.value = false;
  };

  const fallback = (value: any) => {
    return {
      id: value,
      name: settleText.value,
    };
  };

  defineProps<{
    placeholder?: string;
  }>();
</script>

<style lang="less" scoped>
  .su-search {
    width: 100%;
  }
</style>
