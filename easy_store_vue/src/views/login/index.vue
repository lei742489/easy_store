<template>
  <div class="login-page">
    <div v-if="loading" class="startup-loading">
      <icon-loading class="startup-loading-icon" />
      <span>正在启动服务...</span>
    </div>
    <div v-else class="container">
      <div class="logo">
        <img alt="logo" class="ico" src="@/assets/icon.png" />
        <div class="logo-text">{{ appName }}</div>
      </div>
      <LoginBanner />
      <div class="content">
        <img class="login-form-logo" :src="logoBig" alt="logo" />

        <div v-if="mode === 1" class="content-inner">
          <LoginForm @change-mode="handleChangeMode" />
        </div>
        <div v-else>
          <RegForm @change-mode="handleChangeMode" />
        </div>
        <div class="footer">
          <Footer />
        </div>
      </div>
      <FrameMenu v-model="showMaximize" />
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { onBeforeUnmount, onMounted, ref } from 'vue';
  import Footer from '@/components/footer/index.vue';
  import FrameMenu from '@/components/menu/frame-menu.vue';
  import appConfig from '@/config/app';
  import LoginBanner from './components/banner.vue';
  import LoginForm from './components/login-form.vue';
  import RegForm from './components/reg-form.vue';
  import logoBig from '@/assets/logo-big.png';

  const READY_CHECK_INTERVAL = 1000;
  const READY_CHECK_TIMEOUT = 5000;
  const apiBaseUrl = String(import.meta.env.VITE_API_BASE_URL || '')
    .trim()
    .replace(/^['"]|['"]$/g, '')
    .replace(/\/$/, '');

  const { appName } = appConfig;
  const mode = ref(1);
  const showMaximize = ref(false);
  const loading = ref(true);
  let disposed = false;
  let retryTimer: number | undefined;
  let activeController: AbortController | undefined;

  function handleChangeMode(newMode: number) {
    mode.value = newMode;
  }

  const probeApiReady = async () => {
    const controller = new AbortController();
    activeController = controller;
    const timeoutId = window.setTimeout(
      () => controller.abort(),
      READY_CHECK_TIMEOUT
    );

    try {
      const response = await fetch(`${apiBaseUrl}/api/health`, {
        method: 'GET',
        cache: 'no-store',
        headers: {
          Accept: 'application/json',
        },
        signal: controller.signal,
      });
      if (!response.ok) {
        return false;
      }
      const result = await response.json();
      return result?.success === true;
    } catch {
      return false;
    } finally {
      window.clearTimeout(timeoutId);
      if (activeController === controller) {
        activeController = undefined;
      }
    }
  };

  async function waitForApiReady() {
    if (disposed) {
      return;
    }
    if (await probeApiReady()) {
      if (!disposed) {
        loading.value = false;
      }
      return;
    }
    if (!disposed) {
      retryTimer = window.setTimeout(() => {
        retryTimer = undefined;
        waitForApiReady().catch(() => undefined);
      }, READY_CHECK_INTERVAL);
    }
  }

  onMounted(() => {
    waitForApiReady().catch(() => undefined);
  });

  onBeforeUnmount(() => {
    disposed = true;
    activeController?.abort();
    if (retryTimer !== undefined) {
      window.clearTimeout(retryTimer);
    }
  });
</script>

<style lang="less" scoped>
  .login-page {
    width: 100%;
    height: 100vh;
    overflow: hidden;
  }

  .startup-loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    gap: 12px;
    color: var(--color-text-2);
    background: var(--color-bg-1);
  }

  .startup-loading-icon {
    color: rgb(var(--primary-6));
    font-size: 34px;
    animation: startup-loading-rotate 1s linear infinite;
  }

  @keyframes startup-loading-rotate {
    to {
      transform: rotate(360deg);
    }
  }

  .container {
    display: flex;
    width: 100%;
    height: 100%;
    overflow: hidden;

    .banner {
      width: 550px;
      background-image: url(@/assets/images/login-bg.png);
      background-repeat: no-repeat;
      background-size: cover;
    }

    .content {
      position: relative;
      display: flex;
      flex: 1;
      align-items: center;
      justify-content: center;
      padding-bottom: 40px;
      -webkit-app-region: drag;

      .login-form-logo {
        position: absolute;
        top: 12%;
        z-index: 11;
        display: block;
        width: 160px;
        height: auto;
        margin: 0 auto;
        margin-left: 0;
        margin-right: 0;
        
      }

      .content-inner {
        -webkit-app-region: no-drag;
      }
    }

    .footer {
      position: absolute;
      right: 0;
      bottom: 0;
      width: 100%;
    }
  }

  .logo {
    position: fixed;
    top: 24px;
    left: 22px;
    z-index: 10;
    display: inline-flex;
    align-items: center;

    .ico {
      width: 28px;
      height: 28px;
      background: #ffffff;
      border-radius: 8px;
    }

    &-text {
      margin-right: 4px;
      margin-left: 6px;
      color: #5941cd;
      font-size: 20px;
    }
  }
</style>

<style lang="less" scoped>
  @media (max-width: @screen-lg) {
    .container {
      .banner {
        width: 25%;
      }
    }
  }
</style>
