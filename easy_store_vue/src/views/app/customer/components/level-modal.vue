<template>
  <div class="drawer">
    <a-modal
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
          <a-form-item field="title" label="名称">
            <a-input
              v-model="form.title"
              placeholder="请输入名称"
              :max-length="100"
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
  import type { AppCustomerLevel } from '../types/AppCustomerLevel';
  import { add, edit } from '../api/api-AppCustomerLevel';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');

  const defaultForm: AppCustomerLevel = {
    id: undefined,
    title: undefined,
    createTime: undefined,
  };
  const form = reactive<AppCustomerLevel>({ ...defaultForm });
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const showModal = (item: AppCustomerLevel) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-客户等级';
    } else {
      title.value = '新增-客户等级';
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
  };

  const handleOk = async () => {
    const s = await formRef.value.validate();
    console.log(s);
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

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }
</style>
