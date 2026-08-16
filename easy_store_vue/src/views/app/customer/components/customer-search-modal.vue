<template>
  <div class="su-search">
    <a-auto-complete
      v-model="customerName"
      :data="searchData"
      :placeholder="placeholder"
      :filter-option="() => true"
      @select="select"
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
  import { searchKey } from '../api/api-customer';

  const searchData = ref<string[]>([]);
  const customerName = defineModel<string>('customerName');
  const handleSearchKey = async (key: any) => {
    if (!key) {
      searchData.value = [];
      return;
    }
    const res = await searchKey(key);
    searchData.value = res.data;
  };

  const select = (res: any) => {
    console.log(res);
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
