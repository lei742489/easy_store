<template>
  <div class="container">
    <a-card
      class="general-card"
      :title="$t('menu.custom.customerManagement')"
      style="margin-top: 12px"
    >
      <div class="content">
        <!-- 左侧客户分类（树状） -->
        <div class="left-tree">
          <category @change="categoryChange" />
        </div>

        <!-- 右侧客户列表 -->
        <div class="right-list">
          <!-- 这里是客户列表内容 -->
          <customer-list ref="customerList" />
        </div>
      </div>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import category from './components/category-list.vue';
  import CustomerList from './components/customer-list.vue';

  const customerList = ref<InstanceType<typeof CustomerList> | null>(null);

  const categoryChange = (categoryId: number) => {
    customerList.value?.queryByCategoryId(categoryId);
  };
</script>

<style lang="less" scoped>
  .container {
    padding: 0 20px 20px 20px;

    .content {
      display: flex;

      .left-tree {
        width: 16%; // 1/6 宽度
        padding: 12px;
        border-right: 1px solid #e0e0e0;
      }

      .right-list {
        flex: 1;
        padding: 12px;
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
