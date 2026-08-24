<template>
  <div class="su-search">
    <a-auto-complete
      v-model="customerId"
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
  import { searchKey } from '../api/api-customer';

  const searchData = ref<string[]>([]);
  const customerId = defineModel<string>('customerId');
  const loading = ref(false);

  const handleSearch = async (key: string) => {
    const keyword = String(key ?? '').trim();
    customerId.value = keyword;
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
    customerId.value = String(value || '').trim();
  };

  watch(customerId, (value) => {
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
