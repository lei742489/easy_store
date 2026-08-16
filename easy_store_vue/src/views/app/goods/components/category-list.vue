<template>
  <div class="cate">
    <div class="tree">
      <a-tree
        v-if="treeData.length > 0"
        :show-line="true"
        :data="treeData"
        size="large"
        :default-expand-all="false"
        :default-expanded-keys="[0]"
        @select="onSelect"
      >
        <template #extra="nodeData">
          <div
            v-if="nodeData.id !== 0"
            style="position: absolute; right: 8px; top: 10px"
          >
            <a-popconfirm
              :content="`确认删除：${nodeData.title}?`"
              @ok="onDeleteClick(nodeData)"
            >
              <icon-delete
                class="m1"
                style="font-size: 16px; color: #e74c3c; cursor: pointer"
              />
            </a-popconfirm>
          </div>

          <icon-pen-fill
            v-if="nodeData.id !== 0"
            class="m1"
            style="
              position: absolute;
              right: 30px;
              font-size: 16px;
              top: 10px;
              color: #3370ff;
            "
            @click="() => onIconClick(nodeData)"
          />
        </template>
      </a-tree>
    </div>

    <div class="btn">
      <a-button size="small" type="primary" @click="openModal({})"
        >增加分类</a-button
      >
    </div>

    <category-modal ref="modalRef" @ok="fetchData()" />
  </div>
</template>

<script lang="ts" setup>
  import { ref, onMounted } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import categoryModal from './category-modal.vue';
  import { list, remove } from '../api/api-AppGoodsCategory';
  import type { AppGoodsCategory } from '../types/AppGoodsCategory';

  const modalRef = ref<InstanceType<typeof categoryModal> | null>(null);

  const openModal = (item: AppGoodsCategory) => {
    modalRef.value?.showModal(item);
  };

  const emit = defineEmits<{
    (e: 'change', data: number): void;
  }>();

  const treeData = ref<AppGoodsCategory[]>([]);

  const onSelect = (selectedKeys: string[], info: any) => {
    const { node } = info;
    emit('change', node ? node.id || 0 : 0);
  };

  const onIconClick = (item: AppGoodsCategory) => {
    modalRef.value?.showModal(item);
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

  onMounted(() => {
    fetchData(); // 👈 组件挂载完成后调用方法
  });

  const onDeleteClick = async (item: AppGoodsCategory) => {
    await remove(item);
    Message.success('操作成功');
    fetchData();
  };
</script>

<style lang="less" scoped>
  .cate {
    width: 100%;
    .tree {
      max-height: 660px;
      overflow-y: auto;
    }

    .btn {
      margin-top: 15px;
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .m1:hover {
      transform: scale(1.5);
    }
    .m2:hover {
      background: #e74c3c;
      color: #ffffff !important;
    }
  }
</style>
