<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="goodsId"
      :options="dataList"
      :placeholder="placeholder"
      :loading="loading"
      :allow-search="true"
      :field-names="{ value: 'goodsId', label: 'label' }"
      :filter-option="() => true"
      :fallback-option="fallback"
      @search="handleSearch"
      @change="handleChange"
    />
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import { searchKey } from '@/views/app/goods/api/api-AppGoods';

  const dataList = ref<GoodsSearchResult[]>([]);
  const goodsId = defineModel<number | string>('goodsId');
  const goodsText = defineModel<string>('goodsText');
  const loading = ref(false);
  const emit = defineEmits<{
    (e: 'change', data?: GoodsSearchResult): void;
  }>();

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    const res = await searchKey('');
    dataList.value = res.data || [];
    loading.value = false;
  };

  const handleSearch = async (key: string) => {
    const res = await searchKey(key);
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
    const hit = dataList.value.find((item) => `${item.goodsId}` === `${value}`);
    goodsText.value = hit?.label || '';
    emit('change', hit);
  };

  const fallback = (value: any) => ({
    goodsId: value,
    label: goodsText.value || '',
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
