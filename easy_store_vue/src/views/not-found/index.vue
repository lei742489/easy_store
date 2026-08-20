<template>
  <div class="content">
    <a-result class="result" status="404" :subtitle="'未找到该页面'">
    </a-result>
    <div class="current-path">当前路径：{{ route.fullPath }}</div>
    <div class="operation-row">
      <a-button key="back" type="primary" @click="back"> 返 回 </a-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { isLogin } from '@/utils/auth';
  import { DEFAULT_ROUTE_NAME } from '@/router/constants';

  const router = useRouter();
  const route = useRoute();

  onMounted(() => {
    if (route.path === '/') {
      router.replace({
        name: isLogin() ? DEFAULT_ROUTE_NAME : 'login',
      });
    }
  });

  const back = () => {
    // warning： Go to the node that has the permission
    router.replace({
      name: isLogin() ? DEFAULT_ROUTE_NAME : 'login',
    });
  };
</script>

<style scoped lang="less">
  .content {
    // padding-top: 100px;
    position: absolute;
    top: 50%;
    left: 50%;
    margin-left: -95px;
    margin-top: -121px;
    text-align: center;
  }

  .current-path {
    max-width: 520px;
    margin: 0 auto 16px;
    color: var(--color-text-3);
    font-size: 13px;
    line-height: 20px;
    word-break: break-all;
  }
</style>
