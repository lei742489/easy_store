<template>
  <div class="navbar">
    <div class="left-side">
      <a-space @click="toHome()">
        <img alt="logo" class="logo" src="@/assets/icon.png" />
        <a-typography-title :heading="5" class="brand-title">
          {{ appName }}
        </a-typography-title>
        <icon-menu-fold
          v-if="!topMenu && appStore.device === 'mobile'"
          style="font-size: 22px; cursor: pointer"
          @click="toggleDrawerMenu"
        />
      </a-space>
    </div>
    <div class="center-side">
      <Menu v-if="topMenu" />
    </div>
    <ul class="right-side">
      <li>
        <div class="search-view">
          <a-auto-complete
            v-model="quickGoodsKey"
            class="ipt"
            :data="[]"
            :style="{ width: '380px' }"
            placeholder="商品名称或者字母查询..."
            @keydown.enter="handleQuickGoodsSearch"
          >
            <template #suffix>
              <icon-search
                :size="18"
                class="search-icon"
                @click.stop="handleQuickGoodsSearch"
              />
            </template>
          </a-auto-complete>
        </div>
      </li>
      <li>
        <a-tooltip
          :content="
            appStore.theme === 'dark'
              ? $t('settings.navbar.theme.toLight')
              : $t('settings.navbar.theme.toDark')
          "
        >
          <a-button
            class="nav-btn theme-btn"
            type="outline"
            shape="circle"
            @click="handleToggleTheme"
          >
            <template #icon>
              <icon-sun-fill v-if="appStore.theme === 'dark'" />
              <icon-moon-fill v-else />
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <!--      <li>
        <a-tooltip :content="$t('settings.language')">
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="setDropDownVisible"
          >
            <template #icon>
              <icon-language />
            </template>
          </a-button>
        </a-tooltip>
        <a-dropdown trigger="click" @select="changeLocale as any">
          <div ref="triggerBtn" class="trigger-btn"></div>
          <template #content>
            <a-doption
              v-for="item in locales"
              :key="item.value"
              :value="item.value"
            >
              <template #icon>
                <icon-check v-show="item.value === currentLocale" />
              </template>
              {{ item.label }}
            </a-doption>
          </template>
        </a-dropdown>
      </li>-->
      <!--      <li>
        <a-tooltip
          :content="
            theme === 'light'
              ? $t('settings.navbar.theme.toDark')
              : $t('settings.navbar.theme.toLight')
          "
        >
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="handleToggleTheme"
          >
            <template #icon>
              <icon-moon-fill v-if="theme === 'dark'" />
              <icon-sun-fill v-else />
            </template>
          </a-button>
        </a-tooltip>
      </li>-->
      
      <!--      <li>
        <a-tooltip
          :content="
            isFullscreen
              ? $t('settings.navbar.screen.toExit')
              : $t('settings.navbar.screen.toFull')
          "
        >
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="toggleFullScreen"
          >
            <template #icon>
              <icon-fullscreen-exit v-if="isFullscreen" />
              <icon-fullscreen v-else />
            </template>
          </a-button>
        </a-tooltip>
      </li>-->
      <li>
        <a-tooltip :content="$t('settings.title')">
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="setVisible"
          >
            <template #icon>
              <icon-settings />
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <li>
        <a-dropdown trigger="click">
          <a-avatar
            :size="32"
            :style="{ marginRight: '8px', cursor: 'pointer' }"
          >
            <img
              v-if="avatar"
              class="head-ico"
              alt="avatar"
              :src="fixedHeadUrl(avatar)"
            />
            <img
              v-else
              class="head-ico"
              alt="avatar"
              src="@/assets/ico/def_head.png"
            />
          </a-avatar>
          <template #content>
            <a-doption>
              <a-space @click="$router.push('/custom/userInfo')">
                <icon-user />
                <span> 个人中心 </span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="setVisible">
                <icon-settings />
                <span> 系统设置 </span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="handleLogout">
                <icon-export />
                <span> 退出登录 </span>
              </a-space>
            </a-doption>
          </template>
        </a-dropdown>
      </li>
    </ul>
  </div>
  <goods-quote-modal ref="goodsQuoteModalRef" />
</template>

<script lang="ts" setup>
  import { computed, ref, inject } from 'vue';
  import { useAppStore, useUserStore } from '@/store';
  import useUser from '@/hooks/user';
  import Menu from '@/components/menu/index.vue';
  import { useRouter } from 'vue-router';
  import appConfig from '@/config/app';
  import MessageBox from '../message-box/index.vue';
  import GoodsQuoteModal from './goods-quote-modal.vue';

  const { appName } = appConfig;
  const appStore = useAppStore();
  const userStore = useUserStore();
  const { logout } = useUser();
  const router = useRouter();
  const avatar = computed(() => {
    return userStore.avatar;
  });
  const quickGoodsKey = ref('');
  const goodsQuoteModalRef = ref<InstanceType<typeof GoodsQuoteModal> | null>(
    null
  );
  const topMenu = computed(() => appStore.topMenu && appStore.menu);
  const setVisible = () => {
    appStore.updateSettings({ globalSettings: true });
  };
  const refBtn = ref();
  const setPopoverVisible = () => {
    const event = new MouseEvent('click', {
      view: window,
      bubbles: true,
      cancelable: true,
    });
    refBtn.value.dispatchEvent(event);
  };
  const handleLogout = () => {
    logout();
  };
  const handleToggleTheme = () => {
    appStore.toggleTheme(appStore.theme !== 'dark');
  };
  const toggleDrawerMenu = inject('toggleDrawerMenu') as () => void;

  const toHome = () => {
    router.push('/dashboard/workplace');
  };

  const handleQuickGoodsSearch = () => {
    goodsQuoteModalRef.value?.showModal(quickGoodsKey.value);
  };

  const fixedHeadUrl = (url: string) => {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
    const baseImgUrl = `${apiBaseUrl}/api/upload/static`;

    return baseImgUrl + url;
  };
</script>

<style scoped lang="less">
  .navbar {
    display: flex;
    justify-content: space-between;
    height: 100%;
    background: linear-gradient(
      135deg,
      rgb(var(--primary-7)) 0%,
      rgb(var(--primary-6)) 58%,
      rgb(var(--primary-5)) 100%
    );
    border-bottom: 1px solid rgb(var(--primary-7));
  }

  .left-side {
    display: flex;
    align-items: center;
    padding-left: 20px;
    -webkit-app-region: no-drag;
    .logo {
      width: 44px;
      height: 44px;
      padding: 2px;
      border: 2px solid #fff;
      border-radius: 7px;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.16);
    }

    .brand-title {
      margin: 0;
      color: rgb(255,255,255);
      font-size: 17px;
      font-weight: 600;
      line-height: 1;
      user-select: none;
      cursor: pointer;
      letter-spacing: 0;
    }
  }

  .center-side {
    flex: 1;
  }

  .right-side {
    display: flex;
    padding-right: 4px;
    list-style: none;
    -webkit-app-region: no-drag;
    :deep(.locale-select) {
      border-radius: 20px;
    }
    .search-view {
      :deep(.arco-input-wrapper) {
        background-color: #fff;
        border-color: #fff;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
      }

      :deep(.arco-input-wrapper:hover),
      :deep(.arco-input-wrapper.arco-input-focus) {
        background-color: #fff;
        border-color: #fff;
      }

      :deep(.arco-input) {
        background-color: transparent;
        color: #1d2129;
        caret-color: #1d2129;
      }

      :deep(.arco-input::placeholder) {
        color: #86909c;
      }

      .search-icon {
        color: #666;
        cursor: pointer;
      }
    }
    li {
      display: flex;
      align-items: center;
      padding: 0 10px;
    }

    a {
      color: var(--color-text-1);
      text-decoration: none;
    }
    .nav-btn {
      border-color: rgba(255, 255, 255, 0.72);
      color: #fff;
      font-size: 16px;
    }
    .theme-btn {
      :deep(.arco-icon) {
        color: #fff;
      }
    }
    .head-ico {
      width: 34px;
      height: 34px;
    }
    .trigger-btn,
    .ref-btn {
      position: absolute;
      bottom: 14px;
    }
    .trigger-btn {
      margin-left: 14px;
    }
  }
</style>

<style lang="less">
  .message-popover {
    .arco-popover-content {
      margin-top: 0;
    }
  }
</style>
