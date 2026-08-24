<template>
  <div class="su-search">
    <a-auto-complete
      v-model="supplierId"
      :data="searchData"
      :placeholder="placeholder"
      :loading="loading"
      :allow-clear="true"
      :filter-option="() => true"
      @search="handleSearch"
      @select="handleSelect"
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
  const supplierId = defineModel<string>('supplierId');
  const loading = ref(false);

  const handleSearch = async (key: string) => {
    const keyword = String(key ?? '').trim();
    supplierId.value = keyword;
    if (!keyword) {
      searchData.value = [];
      return;
    }
    loading.value = true;
    try {
      const res = await searchKey(keyword);
      searchData.value = res.data || [];
    } finally {
      loading.value = false;
    }
  };

  const handleSelect = (value: string) => {
    supplierId.value = String(value || '').trim();
  };

  watch(supplierId, (value) => {
    if (!value) {
      searchData.value = [];
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
