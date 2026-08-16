<template>
  <h2 class="title">登录密码修改</h2>
  <a-form
    ref="formRef"
    :model="formData"
    class="form"
    :label-col-props="{ span: 8 }"
    :wrapper-col-props="{ span: 16 }"
  >
    <a-form-item
      field="pwd"
      label="登录新密码"
      :rules="[
        { required: true, message: '请输入新密码' },
        { min: 6, message: '密码长度至少 6 位' },
      ]"
    >
      <a-input-password v-model="formData.pwd" placeholder="请输入新密码" />
    </a-form-item>

    <a-form-item
      field="pwd2"
      label="确认密码"
      :rules="[
        { required: true, message: '请再次输入新密码' },
        { validator: validatePwd2 },
      ]"
    >
      <a-input-password v-model="formData.pwd2" placeholder="请输入确认密码" />
    </a-form-item>

    <a-form-item>
      <a-button
        type="primary"
        style="width: 120px; margin-top: 20px"
        @click="handleSubmit"
      >
        保 存
      </a-button>
    </a-form-item>
  </a-form>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import type { FieldRule } from '@arco-design/web-vue';
  import { Message } from '@arco-design/web-vue';
  import { BasicInfoModel } from '@/api/user-center';
  import { useUserStore } from '@/store';
  import { updatePwd } from '@/api/user';

  const formRef = ref();
  const userStore = useUserStore();
  const formData = ref<BasicInfoModel>({
    id: userStore.id,
    pwd: '',
    pwd2: '',
  });

  // 校验“确认密码”与“新密码”一致
  const validatePwd2 = (value: string, callback: (error?: string) => void) => {
    if (!value) {
      callback('请再次输入新密码');
    } else if (value !== formData.value.pwd) {
      callback('两次输入的密码不一致');
    } else {
      callback();
    }
  };

  // 点击保存
  const handleSubmit = () => {
    formRef.value?.validate(async (errors: any) => {
      if (!errors) {
        // 校验通过
        await updatePwd(formData.value);
        Message.success('修改成功！');
        formData.value.pwd = '';
        formData.value.pwd2 = '';
        console.log('表单数据：', formData.value);
      }
    });
  };
</script>

<style scoped lang="less">
  .title {
    text-align: center;
    color: #333;
  }
  .form {
    width: 540px;
    margin: 0 auto;
    margin-top: 30px;
  }
</style>
