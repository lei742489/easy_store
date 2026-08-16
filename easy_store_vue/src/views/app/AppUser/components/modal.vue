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
          <a-form-item
            field="userName"
            label="用户名"
            :rules="[{ required: true, message: '请输入用户名' }]"
          >
            <a-input
              v-model="form.userName"
              placeholder="请输入用户名"
              :max-length="100"
              :disabled="!!form.id"
            />
          </a-form-item>
          <a-form-item
            field="realName"
            label="名称"
            :rules="[{ required: true, message: '请输入名称' }]"
          >
            <a-input
              v-model="form.realName"
              placeholder="请输入名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item
            v-if="!form.id"
            field="password"
            label="初始密码"
            :rules="[
              { required: true, message: '请输入初始密码' },
              { minLength: 6, message: '密码最少输入6位' },
            ]"
          >
            <a-input-password
              v-model="form.password"
              placeholder="请输入初始密码"
              :max-length="32"
            />
          </a-form-item>
          <a-form-item
            field="roleId"
            label="角色"
            :rules="[{ required: true, message: '请选择角色' }]"
          >
            <a-select v-model="form.roleId" placeholder="请选择角色">
              <a-option
                v-for="role in roleOptions"
                :key="role.id"
                :value="role.id"
              >
                {{ role.name }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item field="mobile" label="手机号">
            <a-input
              v-model="form.mobile"
              placeholder="请输入手机号"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="commissionRate" label="提成比例(%)">
            <a-input-number
              v-model="form.commissionRate"
              :min="0"
              :max="100"
              :precision="2"
              placeholder="请输入提成比例"
            />
          </a-form-item>
          <a-form-item field="remarks" label="备注">
            <a-input
              v-model="form.remarks"
              placeholder="请输入备注"
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
  import type { AppRole } from '@/views/app/AppRole/types/AppRole';
  import { list as getRoleList } from '@/views/app/AppRole/api/api-AppRole';
  import type { AppUser } from '../types/AppUser';
  import { add, edit } from '../api/api-AppUser';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const roleOptions = ref<AppRole[]>([]);

  const defaultForm: AppUser = {
    id: undefined,
    userName: undefined,
    realName: undefined,
    password: undefined,
    remarks: undefined,
    mobile: undefined,
    commissionRate: 0,
    roleId: undefined,
    status: 1,
  };
  const form = reactive<AppUser>({ ...defaultForm });
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const loadRoleOptions = async () => {
    const { data } = await getRoleList();
    roleOptions.value = data || [];
  };

  const showModal = async (item: AppUser) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-员工管理';
    } else {
      title.value = '新增-员工管理';
    }
    await loadRoleOptions();
    Object.assign(form, defaultForm);
    if (Object.keys(item).length !== 0) Object.assign(form, item);
    form.password = undefined;
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
