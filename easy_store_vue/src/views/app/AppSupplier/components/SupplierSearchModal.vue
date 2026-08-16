<template>
  <div class="su-search">
    <a-auto-complete
      v-model="supplierName"
      :data="searchData"
      :placeholder="placeholder"
      :filter-option="() => true"
      @search="handleSearchKey"
    >
      <template #option="{ data }">
        <span>{{ data.value }}</span>
      </template>
    </a-auto-complete>
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch } from 'vue';
  import { searchKey } from '@/views/app/AppSupplier/api/api-AppSupplier';

  const searchData = ref<string[]>([]);
  const supplierName = defineModel<string>('supplierName');

  const handleSearchKey = async (key: any) => {
    if (!key) {
      searchData.value = [];
      return;
    }
    const res = await searchKey(key);
    searchData.value = res.data;
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
