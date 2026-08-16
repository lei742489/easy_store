<template>
  <a-col class="banner">
    <div class="c-top">
      <a-typography-title :heading="5" style="margin-top: 0">
        {{ $t('workplace.welcome') }} {{ userInfo.realName || userInfo.userName }}
      </a-typography-title>
      <span class="sub-txt">当前时间：{{ currentTime }}</span>
    </div>
    <a-divider class="panel-border" />
  </a-col>
</template>

<script lang="ts" setup>
  import { computed, ref, onMounted, onUnmounted } from 'vue';
  import { useUserStore } from '@/store';

  const currentTime = ref('');
  const userStore = useUserStore();
  const userInfo = computed(() => {
    return {
      userName: userStore.userName,
      realName: userStore.realName,
    };
  });

  // 定义更新时间的函数
  const updateTime = () => {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const hh = String(now.getHours()).padStart(2, '0');
    const mi = String(now.getMinutes()).padStart(2, '0');
    const ss = String(now.getSeconds()).padStart(2, '0');
    currentTime.value = `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`;
  };

  let timer: any;

  onMounted(() => {
    updateTime(); // 初始化时间
    timer = setInterval(updateTime, 1000); // 每秒更新
  });

  onUnmounted(() => {
    clearInterval(timer); // 页面卸载时清除定时器
  });
</script>

<style scoped lang="less">
  .banner {
    width: 100%;
    padding: 20px 20px 0 20px;
    background-color: var(--color-bg-2);
    border-radius: 4px 4px 0 0;
  }

  :deep(.arco-icon-home) {
    margin-right: 6px;
  }

  .c-top {
    width: 100%;
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;

    .sub-txt {
      color: #5e85ab;
    }
  }
</style>
