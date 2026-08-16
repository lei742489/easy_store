<template>
  <div class="login-form-wrapper">
    <div class="login-form-title">
      {{ $t('login.form.reg', { appName }) }}
    </div>
    <div class="login-form-sub-title">{{ $t('login.form.subTitle') }}</div>
    <div class="login-form-error-msg">{{ errorMessage }}</div>
    <a-form
      ref="loginForm"
      :model="userInfo"
      class="login-form"
      layout="vertical"
      @submit="handleSubmit"
    >
      <a-form-item
        field="username"
        :rules="[{ required: true, message: $t('login.form.userName.errMsg') }]"
        :validate-trigger="['change', 'blur']"
        hide-label
      >
        <a-input
          v-model="userInfo.username"
          :placeholder="$t('login.form.userName.placeholder')"
        >
          <template #prefix>
            <icon-user />
          </template>
        </a-input>
      </a-form-item>
      <a-form-item
        field="password"
        :rules="[
          { required: true, message: $t('login.form.password.errMsg') },
          {
            validator: (value: any, cb: any) => {
              if (!value || value.length<6 || value.length>20)
                return cb($t('login.form.password.lengthErrMsg'))

              return cb()
            }
          }
        ]"
        :validate-trigger="['change', 'blur']"
        hide-label
      >
        <a-input-password
          v-model="userInfo.password"
          :placeholder="$t('login.form.reg.password.placeholder')"
          allow-clear
        >
          <template #prefix>
            <icon-lock />
          </template>
        </a-input-password>
      </a-form-item>
      <a-form-item
        field="password2"
        :rules="[
          { required: true, message: $t('login.form.password.errMsg') },
          {
            validator: (value: any, cb: any) => {
              if (value !== userInfo.password) {
                return cb($t('login.form.password.InconsistencyErrMsg'))
              }
              return cb() // 验证通过
            }
          }
        ]"
        :validate-trigger="['change', 'blur']"
        hide-label
      >
        <a-input-password
          v-model="userInfo.password2"
          :placeholder="$t('login.form.reg.password2.placeholder')"
          allow-clear
        >
          <template #prefix>
            <icon-lock />
          </template>
        </a-input-password>
      </a-form-item>
      <!-- 图形验证码 -->
      <a-form-item
        field="captcha"
        :rules="[{ required: true, message: '请输入验证码' }]"
        :validate-trigger="['change', 'blur']"
        hide-label
      >
        <div class="captcha-wrapper">
          <a-input
            v-model="userInfo.captcha"
            :placeholder="$t('login.form.reg.captcha.placeholder')"
            allow-clear
          />
          <img
            :src="captchaUrl"
            class="captcha-img"
            :title="$t('login.form.reg.captcha.refresh')"
            @click="refreshCaptcha"
          />
        </div>
      </a-form-item>
      <a-space :size="16" direction="vertical">
        <a-button type="primary" html-type="submit" long :loading="loading">
          {{ $t('login.form.register') }}
        </a-button>
        <a-button
          type="text"
          long
          class="login-form-register-btn"
          @click="changeMode(1)"
        >
          {{ $t('login.form.loginBack') }}
        </a-button>
      </a-space>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
  import { ref, reactive } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import { ValidatedError } from '@arco-design/web-vue/es/form/interface';
  import { useI18n } from 'vue-i18n';
  import { useUserStore } from '@/store';
  import useLoading from '@/hooks/loading';
  import type { RegData } from '@/api/user';
  import appConfig from '@/config/app';

  const { appName } = appConfig;
  const router = useRouter();
  const { t } = useI18n();
  const errorMessage = ref('');
  const { loading, setLoading } = useLoading();
  const userStore = useUserStore();
  const captchaUrl = ref('');

  // 定义要发出的事件
  const emit = defineEmits<{
    (e: 'changeMode', mode: number): void;
  }>();

  const userInfo = reactive({
    username: '',
    password: '',
    password2: '',
    captcha: '',
  });
  const changeMode = (mode: number) => {
    emit('changeMode', mode);
  };
  const handleSubmit = async ({
    errors,
    values,
  }: {
    errors: Record<string, ValidatedError> | undefined;
    values: Record<string, any>;
  }) => {
    if (loading.value) return;
    if (!errors) {
      setLoading(true);
      try {
        // 注册逻辑？
        await userStore.register(values as RegData);
        const { redirect, ...othersQuery } = router.currentRoute.value.query;
        router.push({
          name: (redirect as string) || 'Workplace',
          query: {
            ...othersQuery,
          },
        });

        Message.success(t('login.form.login.success'));
      } catch (err) {
        errorMessage.value = (err as Error).message;
      } finally {
        setLoading(false);
      }
    }
  };
  // 刷新验证码
  const refreshCaptcha = () => captchaUrl.value;
</script>

<style lang="less" scoped>
  .login-form {
    &-wrapper {
      width: 320px;
    }

    &-title {
      user-select: none;
      color: var(--color-text-1);
      font-weight: 500;
      font-size: 24px;
      line-height: 32px;
    }

    &-sub-title {
      user-select: none;
      color: var(--color-text-3);
      font-size: 16px;
      line-height: 24px;
    }

    &-error-msg {
      height: 32px;
      color: rgb(var(--red-6));
      line-height: 32px;
    }

    &-password-actions {
      display: flex;
      justify-content: space-between;
    }

    &-register-btn {
      color: var(--color-text-3) !important;
    }

    .captcha-wrapper {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .captcha-img {
      width: 96px;
      height: 34px;
      border: 1px solid #ccc;
      cursor: pointer;
      user-select: none;
    }
  }
</style>
