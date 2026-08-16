<template>
  <div class="su-search" @click="fetchList">
    <a-select
      v-model="userId"
      :options="dataList"
      :placeholder="placeholder"
      :loading="loading"
      :allow-search="true"
      :field-names="{ value: 'id', label: 'displayName' }"
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

  type UserOption = AppUser & { displayName: string };

  const dataList = ref<UserOption[]>([]);
  const userId = defineModel<number>('userId');
  const loading = ref(false);

  const normalizeUsers = (users: AppUser[]) =>
    users.map((user) => ({
      ...user,
      displayName: user.realName || user.userName || String(user.id || ''),
    }));

  const fetchList = async () => {
    if (dataList.value.length > 0) return;
    loading.value = true;
    try {
      const res = await list();
      dataList.value = normalizeUsers(res.data || []);
    } finally {
      loading.value = false;
    }
  };

  const handleSearch = async () => {
    const res = await list();
    dataList.value = normalizeUsers(res.data || []);
  };

  watch(
    userId,
    (value) => {
      if (value !== undefined && value !== null) {
        fetchList();
      }
    },
    { immediate: true }
  );

  defineProps<{
    placeholder?: string;
  }>();
</script>

<style lang="less" scoped>
  .su-search {
    width: 100%;
  }
</style>
