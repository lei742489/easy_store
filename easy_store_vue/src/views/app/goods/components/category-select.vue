<template>
  <a-modal
    :visible="visible"
    :ok-loading="loading"
    title="分类选择"
    :width="300"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="content">
      <a-tree
        v-if="treeData.length > 0"
        :show-line="true"
        :data="treeData"
        size="large"
        :default-expand-all="false"
        :default-expanded-keys="[0]"
        @select="onSelect"
      ></a-tree>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import { AppGoodsCategory } from '@/views/app/goods/types/AppGoodsCategory';
  import { list } from '@/views/app/goods/api/api-AppGoodsCategory';

  const visible = ref(false);
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: number): void;
  }>();

  const treeData = ref<AppGoodsCategory[]>([]);
  const selectedId = ref();

  const onSelect = (selectedKeys: string[], info: any) => {
    const { node } = info;
    selectedId.value = node ? node.id || -1 : -1;
  };

  const fetchData = async () => {
    treeData.value = [];
    const res = await list();
    const dataList = res.data;
    dataList.forEach((d) => {
      d.key = d.id;
      if (!d.children) d.children = [];
    });

    treeData.value = dataList;
  };

  const showModal = () => {
    visible.value = true;
    if (treeData.value.length <= 0) {
      fetchData();
    }
  };

  const handleCancel = () => {
    visible.value = false;
    selectedId.value = undefined;
  };
  const handleOk = () => {
    if (selectedId.value && selectedId.value >= 0) {
      emit('ok', selectedId.value);
    }
    handleCancel();
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .content {
    display: flex;
    flex-direction: column;
    max-height: 500px;
    overflow-y: scroll;
    div {
      display: flex;
      flex-direction: column;
    }
  }
</style>
