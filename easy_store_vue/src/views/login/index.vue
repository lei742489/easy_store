<template>
  <a-spin :loading="loading" tip="加载中..." style="width: 100%">
    <div class="container">
      <div class="logo">
        <img alt="logo" class="ico" src="@/assets/icon.png" />
        <div class="logo-text">{{ appName }}</div>
      </div>
      <LoginBanner />
      <div class="content">
        <div v-if="mode == 1" class="content-inner">
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
  </a-spin>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';
  import Footer from '@/components/footer/index.vue';
  import FrameMenu from '@/components/menu/frame-menu.vue';
  import axios from 'axios';
  import appConfig from '@/config/app';
  import LoginBanner from './components/banner.vue';
  import LoginForm from './components/login-form.vue';
  import RegForm from './components/reg-form.vue';

  const { appName } = appConfig;
  const mode = ref(1);
  const showMaximize = ref(false);
  const loading = ref(true);

  function handleChangeMode(newMode: number) {
    mode.value = newMode;
  }

  const apiTest = () => {
    return axios.post<any>(
      '/api/test',
      {},
      {
        timeout: 10000, // 单位毫秒
      }
    );
  };

  const testReady = async () => {
    try {
      await apiTest();
    } catch (err) {
      // Ignore readiness probe errors; the loading state is cleared below.
    } finally {
      loading.value = false;
    }
  };

  testReady();
</script>

<style lang="less" scoped>
  .container {
    display: flex;
    height: 100vh;

    .banner {
      width: 550px;
      //background: linear-gradient(163.85deg, #1d2129 0%, #00308f 100%);
      background-image: url(@/assets/images/login-bg.png);
      background-size: cover;
      background-repeat: no-repeat;
    }

    .content {
      position: relative;
      display: flex;
      flex: 1;
      align-items: center;
      justify-content: center;
      padding-bottom: 40px;
      -webkit-app-region: drag;

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
  // responsive
  @media (max-width: @screen-lg) {
    .container {
      .banner {
        width: 25%;
      }
    }
  }
</style>
