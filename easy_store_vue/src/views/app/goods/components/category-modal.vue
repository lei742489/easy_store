<template>
  <div class="modal">
    <a-modal
      :visible="visible"
      :ok-loading="loading"
      @ok="handleOk"
      @cancel="handleCancel"
    >
      <template #title> {{ title }}分类</template>
      <div>
        <a-form ref="formRef" :model="form">
          <a-form-item
            field="parentId"
            label="上级分类"
            :rules="[{ required: true, message: '请选择上级分类' }]"
          >
            <a-tree-select
              v-if="treeData.length > 0"
              v-model="form.parentId"
              :data="treeData"
              placeholder="请选择上级分类..."
              :field-names="{ key: 'id' }"
              :filter-tree-node="filterTreeNode"
              :allow-search="true"
            ></a-tree-select>
          </a-form-item>
          <a-form-item
            field="title"
            label="分类名称"
            :rules="[{ required: true, message: '请输入分类名称' }]"
          >
            <a-input
              v-model="form.title"
              placeholder="请输入分类名称"
              :max-length="40"
            />
          </a-form-item>
        </a-form>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { AppGoodsCategory } from '../types/AppGoodsCategory';
  import { add, edit, list } from '../api/api-AppGoodsCategory';

  const visible = ref(false);
  const title = ref('');
  const formRef = ref();
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const defaultForm: AppGoodsCategory = {
    id: undefined,
    title: '',
    parentId: 0,
  };

  const form = reactive<AppGoodsCategory>({ ...defaultForm });

  const treeData = ref<AppGoodsCategory[]>([]);

  const handleCancel = () => {
    Object.assign(form, defaultForm);
    treeData.value = [];
    visible.value = false;
  };

  function setDisabledById(sList: AppGoodsCategory[], targetId: number): void {
    if (!targetId) return;
    sList.forEach((item) => {
      if (item.id === targetId) {
        item.disabled = true;
      }
      if (item.children && item.children.length > 0) {
        setDisabledById(item.children, targetId); // 递归处理子节点
      }
    });
  }

  const fetchData = async () => {
    const res = await list();
    const dataList = res.data;
    dataList.forEach((d) => {
      if (!d.children) d.children = [];
    });
    treeData.value = dataList;
    setDisabledById(treeData.value, form.id || -1);
  };

  const showModal = (item: AppGoodsCategory) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑';
    } else {
      title.value = '新增';
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
    fetchData();
  };

  const handleOk = async () => {
    const s = await formRef.value.validate();
    if (!s) {
      // 验证通过后可继续操作

      loading.value = true;
      console.log('验证通过，提交表单数据:', form);
      try {
        if (!form.id) {
          await add(form);
        } else {
          await edit(form);
        }
      } finally {
        loading.value = false;
      }

      Message.success('操作成功');
      handleCancel();
      emit('ok', 1);
    } else {
      // 验证未通过
      console.log('验证失败.');
    }
  };

  const filterTreeNode = (searchValue: string, nodeData: AppGoodsCategory) => {
    const t = nodeData.title || '';
    const pyCode = nodeData.pyCode || '';
    const key = searchValue.toLowerCase();
    return t.toLowerCase().indexOf(key) > -1 || pyCode.indexOf(key) > -1;
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .modal {
    display: flex;
    flex-direction: column;

    div {
      display: flex;
      flex-direction: column;
    }
  }
</style>
