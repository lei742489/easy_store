<template>
  <h2 class="title">基本信息修改</h2>
  <a-form
    ref="formRef"
    :model="formData"
    class="form"
    :label-col-props="{ span: 8 }"
    :wrapper-col-props="{ span: 16 }"
  >
    <a-form-item
      v-if="isRoot"
      field="companyName"
      label="公司/店铺名称"
      :rules="[
        {
          required: true,
          message: '请输入公司/店铺名称',
        },
      ]"
    >
      <a-input
        v-model="formData.companyName"
        placeholder="请输入公司/店铺名称"
      />
    </a-form-item>
    <a-form-item field="email" label="邮箱">
      <a-input v-model="formData.email" placeholder="请输入邮箱地址" />
    </a-form-item>
    <a-form-item field="realName" label="姓名">
      <a-input v-model="formData.realName" placeholder="请输入姓名" />
    </a-form-item>

    <a-form-item field="mobile" label="电话">
      <a-input v-model="formData.mobile" placeholder="请输入联系电话" />
    </a-form-item>

    <a-form-item v-if="isRoot" field="address" label="详细地址">
      <a-input v-model="formData.address" placeholder="请输入详细地址" />
    </a-form-item>

    <a-form-item>
      <a-button type="primary" style="width: 120px" @click="validate">
        保 存
      </a-button>
    </a-form-item>
  </a-form>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref } from 'vue';
  import { FormInstance } from '@arco-design/web-vue/es/form';
  import { BasicInfoModel } from '@/api/user-center';
  import { edit as userEdit, getUserInfo } from '@/api/user';
  import { useUserStore } from '@/store';
  import { Message } from '@arco-design/web-vue';

  const formRef = ref<FormInstance>();
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const formData = ref<BasicInfoModel>({
    id: userStore.id,
    email: userStore.email,
    realName: userStore.realName,
    address: userStore.address,
    mobile: userStore.mobile,
    companyName: userStore.companyName,
  });

  const syncFormData = () => {
    formData.value = {
      id: userStore.id,
      email: userStore.email,
      realName: userStore.realName,
      address: userStore.address,
      mobile: userStore.mobile,
      companyName: userStore.companyName,
    };
  };

  const getInfo = async () => {
    const res = await getUserInfo();
    userStore.setInfo(res.data);
    syncFormData();
  };
  const validate = async () => {
    const res = await formRef.value?.validate();
    if (!res) {
      await userEdit(formData.value);
      Message.success('操作成功');
      getInfo();
    }
  };
  const reset = async () => {
    await formRef.value?.resetFields();
  };

  onMounted(async () => {
    if (!userStore.id) {
      await userStore.info();
    }
    syncFormData();
  });
</script>

<style scoped lang="less">
  .title {
    text-align: center;
    color: #333;
  }
  .form {
    width: 540px;
    margin: 0 auto;
    margin-top: 12px;
  }
</style>
