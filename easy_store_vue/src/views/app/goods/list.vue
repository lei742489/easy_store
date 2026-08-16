<template>
  <div class="container">
    <div class="title">{{ $t('menu.custom.inventoryManagement') }}</div>
    <div class="content">
      <!-- 左侧客户分类（树状） -->
      <div class="left-tree">
        <category @change="categoryChange" />
      </div>

      <!-- 右侧客户列表 -->
      <div class="right-list">
        <!-- 这里是商品列表内容 -->
        <goods-list ref="refList"></goods-list>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import category from './components/category-list.vue';
  import GoodsList from './components/goods-list.vue';

  const refList = ref<InstanceType<typeof GoodsList> | null>(null);

  const categoryChange = (categoryId: number) => {
    refList.value?.queryByCategoryId(categoryId);
  };
</script>

<style lang="less" scoped>
  .container {
    .title {
      margin-top: 12px;
      padding: 12px 0 12px 20px;
      border-bottom: 1px solid #e0e0e0;
      color: #222;
      font-weight: 500;
      line-height: 1.5715;
      font-size: 16px;
      background: #ffffff;
    }

    .content {
      display: flex;
      .left-tree {
        width: 17%; // 1/6 宽度
        padding: 12px;
        border-right: 1px solid #e0e0e0;
        background: #ffffff;
      }

      .right-list {
        flex: 1;
        padding: 10px;
        background-color: #fff;
      }

      .tree-placeholder,
      .list-placeholder {
        border: 1px dashed #aaa;
        padding: 16px;
        color: #888;
        text-align: center;
      }
    }
  }
</style>
