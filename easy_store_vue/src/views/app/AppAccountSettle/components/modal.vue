<template>
  <div class="drawer">
    <a-drawer
      width="40%"
      :visible="visible"
      unmount-on-close
      :mask-closable="true"
      :ok-loading="loading"
      @ok="handleOk"
      @cancel="handleCancel"
    >
      <template #title> {{ title }} </template>
      <div>
        <a-form ref="formRef" :model="form">
          <a-form-item
            field="name"
            label="帐户名称"
            :rules="[{ required: true, message: '请输入帐户名称' }]"
          >
            <a-input
              v-model="form.name"
              placeholder="请输入帐户名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="typeId" label="帐户分类">
            <a-tree-select
              v-model="form.typeId"
              :data="treeData"
              placeholder="请选择..."
              :field-names="{ key: 'id' }"
              :allow-search="true"
              :fallback-option="fallback"
              @popup-visible-change="fetchTypeList"
            ></a-tree-select>
          </a-form-item>
          <a-form-item field="bankName" label="银行名称">
            <a-input
              v-model="form.bankName"
              placeholder="请输入银行名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="bankCard" label="银行卡号">
            <a-input
              v-model="form.bankCard"
              placeholder="请输入银行卡号"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="initPrc" label="初始余额">
            <a-input-number
              v-model="form.initPrc"
              placeholder="请输入初始余额"
              :disabled="true"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="curPrc" label="当前余额">
            <a-input-number
              v-model="form.curPrc"
              placeholder="请输入当前余额"
              :disabled="form.id != undefined"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              :max-length="100"
            />
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type {
    AppAccountSettle,
    AppAccountSettleType,
  } from '../types/AppAccountSettle';
  import { add, edit, listByType } from '../api/api-AppAccountSettle';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const treeData = ref<AppAccountSettleType[]>([]);

  const defaultForm: AppAccountSettle = {
    id: undefined,
    name: undefined,
    typeId: undefined,
    bankName: undefined,
    bankCard: undefined,
    initPrc: undefined,
    curPrc: undefined,
    note: undefined,
  };
  const form = reactive<AppAccountSettle>({ ...defaultForm });
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const fetchTypeList = async () => {
    if (treeData.value.length > 0) return;
    const res = await listByType();
    treeData.value = res.data;
  };

  const showModal = (item: AppAccountSettle) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-结算帐户';
    } else {
      title.value = '新增-结算帐户';
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
    treeData.value = [];
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
    }
  };

  const fallback = (key: any) => {
    return {
      key: key || '',
      title: form.typeId_dictText || '',
    };
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }
</style>
