<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="userId"
      :options="dataList"
      :placeholder="placeholder"
      :loading="loading"
      :allow-search="true"
      :field-names="{ value: 'id', label: 'realName' }"
      :filter-option="() => true"
      @search="handleSearch"
    >
    </a-select>
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch } from 'vue';
  import { list } from '@/views/app/AppUser/api/api-AppUser';
  import { AppUser } from '@/views/app/AppUser/types/AppUser';

  const dataList = ref<AppUser[]>([]);
  const userId = defineModel<number>('userId');
  const loading = ref(false);

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    const res = await list();
    dataList.value = res.data;
    loading.value = false;
  };

  const handleSearch = async () => {
    const res = await list();
    dataList.value = res.data;
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
