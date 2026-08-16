<template>
  <a-layout-footer class="footer">
    <div class="f-left">当前版本：{{ version }}</div>
    <div class="f-right">
      <icon-cloud class="ico" :size="18" />
      <a-link class="t1">数据同步</a-link>
    </div>
  </a-layout-footer>
</template>

<script lang="ts" setup>
  const version = import.meta.env.VITE_APP_VERSION;
</script>

<style lang="less" scoped>
  .footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-direction: row;
    height: 30px;
    color: var(--color-text-2);
    text-align: center;
    background: #f9f9f9;
    position: fixed;
    z-index: 999;
    bottom: 0;
    left: 0;
    width: 100%;
    border-top: 1px solid #eaeaea;
    .f-left {
      display: flex;
      align-items: center;
      flex-direction: row;
      padding-left: 16px;
      color: #666;
      gap: 6px;
    }

    .f-right {
      cursor: pointer;
      padding-right: 16px;
      display: flex;
      align-items: center;
      flex-direction: row;
      .ico {
        color: #5941cd;
      }
    }
  }
</style>
