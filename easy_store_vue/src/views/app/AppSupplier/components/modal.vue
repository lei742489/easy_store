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
            label="供应商名称"
            :rules="[{ required: true, message: '请输入供应商名称' }]"
          >
            <a-input
              v-model="form.name"
              placeholder="请输入供应商名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="contactName" label="联系人">
            <a-input
              v-model="form.contactName"
              placeholder="请输入联系人"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="mobile" label="手机">
            <a-input
              v-model="form.mobile"
              placeholder="请输入手机"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="phone" label="电话">
            <a-input
              v-model="form.phone"
              placeholder="请输入电话"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="mail" label="邮件">
            <a-input
              v-model="form.mail"
              placeholder="请输入邮件"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="postal" label="邮编">
            <a-input
              v-model="form.postal"
              placeholder="请输入邮编"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="address" label="详细地址">
            <a-input
              v-model="form.address"
              placeholder="请输入详细地址"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="qq" label="QQ号">
            <a-input
              v-model="form.qq"
              placeholder="请输入QQ号"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              :max-length="300"
            />
          </a-form-item>

          <a-form-item field="defPayable" label="初期应付款">
            <a-input-number
              v-model="form.defPayable"
              :disabled="true"
              placeholder="请输入初期应付款"
              :max-length="100"
              :min="0"
            />
          </a-form-item>
          <a-form-item field="payable" label="当前应付款">
            <a-input-number
              v-model="form.payable"
              placeholder="请输入当前应付款"
              :max-length="100"
              :disabled="form.id !== undefined"
            />
          </a-form-item>
          <a-form-item field="status" label="状态">
            <a-select v-model="form.status" placeholder="请选择 ...">
              <a-option :value="1">启用</a-option>
              <a-option :value="0">禁用</a-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { AppSupplier } from '../types/AppSupplier';
  import { add, edit } from '../api/api-AppSupplier';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');

  const defaultForm: AppSupplier = {
    id: undefined,
    name: undefined,
    contactName: undefined,
    mobile: undefined,
    phone: undefined,
    mail: undefined,
    postal: undefined,
    address: undefined,
    qq: undefined,
    note: undefined,
    status: 1,
    defPayable: undefined,
    payable: undefined,
    createTime: undefined,
  };
  const form = reactive<AppSupplier>({ ...defaultForm });
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const showModal = (item: AppSupplier) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-供应商管理';
    } else {
      title.value = '新增-供应商管理';
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
