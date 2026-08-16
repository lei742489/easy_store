<template>
  <div class="select-tree-select">
    <a-tree-select
      :model-value="categoryId"
      :loading="categoryLoading"
      :fallback-option="categoryFallback"
      :data="categoryList"
      placeholder="请选择..."
      :field-names="{ key: 'id' }"
      :filter-tree-node="filterCategoryTreeNode"
      :allow-search="true"
      :allow-clear="true"
      :default-expand-all="false"
      :default-expanded-keys="[0]"
      @popup-visible-change="fetchCategoryData"
      @update:model-value="onCategoryChange"
    ></a-tree-select>
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import type { AppGoodsCategory } from '@/views/app/goods/types/AppGoodsCategory';
  import { list as getCategoryList } from '@/views/app/goods/api/api-AppGoodsCategory';

  const categoryLoading = ref(false);
  const categoryId = defineModel<number>('categoryId');
  const categoryText = defineModel<string>('categoryText');
  const categoryList = ref<AppGoodsCategory[]>([]);

  const fetchCategoryData = async (e: any) => {
    if (categoryList.value.length !== 0) return;

    categoryLoading.value = true;
    categoryList.value = (await getCategoryList()).data;
    categoryLoading.value = false;
  };

  const categoryFallback = (key: any) => {
    return {
      key: key || 0,
      title: categoryText.value || '',
    };
  };

  const filterCategoryTreeNode = (
    searchValue: string,
    nodeData: AppGoodsCategory
  ) => {
    const t = nodeData.title || '';
    const pyCode = nodeData.pyCode || '';
    const key = searchValue.toLowerCase();
    return t.toLowerCase().indexOf(key) > -1 || pyCode.indexOf(key) > -1;
  };

  function onCategoryChange(value: number, node: AppGoodsCategory) {
    categoryId.value = value;
    categoryText.value = node?.title || '';
  }

  defineExpose({ fetchCategoryData });
</script>

<style lang="less" scoped>
  .select-tree-select {
  }
</style>
