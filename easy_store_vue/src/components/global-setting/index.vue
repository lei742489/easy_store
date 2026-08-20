<template>
  <div v-if="!appStore.navbar" class="fixed-settings" @click="setVisible">
    <a-button type="primary">
      <template #icon>
        <icon-settings />
      </template>
    </a-button>
  </div>
  <a-drawer
    :width="300"
    unmount-on-close
    :visible="visible"
    :cancel-text="$t('settings.close')"
    :ok-text="$t('settings.copySettings')"
    @ok="copySettings"
    @cancel="cancel"
  >
    <template #title> {{ $t('settings.title') }} </template>
    <div class="theme-block">
      <h5 class="theme-title">{{ $t('settings.themeColor') }}</h5>
      <div class="theme-colors">
        <button
          v-for="item in themeColors"
          :key="item.value"
          class="theme-color"
          :class="{ selected: appStore.themeColor === item.value }"
          :style="{ backgroundColor: item.value }"
          :title="item.label"
          type="button"
          @click="appStore.setThemeColor(item.value)"
        >
          <icon-check v-if="appStore.themeColor === item.value" />
        </button>
      </div>
    </div>

    <Block :options="contentOpts" :title="$t('settings.content')" />
    <Block :options="othersOpts" :title="$t('settings.otherSettings')" />

    <div v-if="isElectron" class="display-block">
      <h5 class="theme-title">显示缩放</h5>
      <div class="zoom-row">
        <a-slider
          v-model="zoomPercent"
          class="zoom-slider"
          :min="50"
          :max="150"
          :step="5"
          :format-tooltip="formatZoomTooltip"
          @change="handleZoomChange"
        />
        <a-input-number
          v-model="zoomPercent"
          class="zoom-input"
          :min="50"
          :max="150"
          :step="5"
          hide-button
          @change="handleZoomChange"
        >
          <template #suffix>%</template>
        </a-input-number>
      </div>
      <a-space class="zoom-actions">
        <a-button size="small" @click="setZoomPercent(85)">85%</a-button>
        <a-button size="small" @click="setZoomPercent(90)">90%</a-button>
        <a-button size="small" @click="setZoomPercent(100)">100%</a-button>
      </a-space>
    </div>

    <div v-if="isElectron" class="printer-block">
      <h5 class="theme-title">打印机设置</h5>
      <div class="printer-row">
        <span>当前打印机</span>
        <a-tag color="arcoblue">{{ currentPrinterName }}</a-tag>
      </div>


      <a-space class="printer-actions">
        <a-button
          type="primary"
          size="small"
          :loading="printerLoading"
          @click="handleSelectPrinter"
        >
          <template #icon>
            <icon-printer />
          </template>
          选择打印机
        </a-button>
        <a-button
          size="small"
          :loading="printerLoading"
          @click="loadPrinterSettings"
        >
          刷新
        </a-button>
      </a-space>
    </div>
    <div v-if="isRoot" class="data-reset-block">
      <h5 class="theme-title">数据清零</h5>
      <p class="data-reset-description">
        清空单据、货品、分类、客户、供应商、角色和流水数据，保留老板帐号及基础配置。
      </p>
      <a-button status="danger" long @click="handleResetData">
        <template #icon>
          <icon-delete />
        </template>
        数据清零
      </a-button>
    </div>
  </a-drawer>
</template>

<script lang="ts" setup>
  import { computed, ref, watch } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import { useI18n } from 'vue-i18n';
  import { useClipboard } from '@vueuse/core';
  import type { ElectronPrinterSettings } from '@/api/electron/electron-api';
  import {
    getZoomFactor,
    getPrinterSettings,
    isElectronRuntime,
    selectRawPrinter,
    setZoomFactor,
  } from '@/api/electron/electron-api';
  import { openPasswordModal } from '@/api/passwordVerification';
  import {
    resetSystemData,
    verifySystemResetPassword,
  } from '@/api/user';
  import { useAppStore, useUserStore } from '@/store';
  import { THEME_COLOR_PRESETS } from '@/store/modules/app';
  import Block from './block.vue';

  const emit = defineEmits(['cancel']);

  const appStore = useAppStore();
  const userStore = useUserStore();
  const themeColors = THEME_COLOR_PRESETS;
  const { t } = useI18n();
  const { copy } = useClipboard();
  const visible = computed(() => appStore.globalSettings);
  const isElectron = computed(() => isElectronRuntime());
  const isRoot = computed(() => userStore.isRoot === 1);
  const printerLoading = ref(false);
  const printerSettings = ref<ElectronPrinterSettings | null>(null);
  const zoomPercent = ref(100);
  const formatZoomTooltip = (value: number) => `${value}%`;
  const printerSourceMap: Record<string, string> = {
    'selected': '手动选择',
    'default-dot-matrix': '系统默认针式打印机',
    'matched-dot-matrix': '自动匹配常用针式打印机',
    'default': '系统默认打印机',
    'missing': '未找到打印机',
  };
  const currentPrinterName = computed(
    () => printerSettings.value?.effectivePrinterName || '未检测到'
  );
  const defaultPrinterName = computed(
    () => printerSettings.value?.defaultPrinterName || '未检测到'
  );
  const printerSourceText = computed(() => {
    const source = printerSettings.value?.effectivePrinterSource || 'missing';
    return printerSourceMap[source] || source;
  });
  const contentOpts = computed(() => [
    { name: 'settings.navbar', key: 'navbar', defaultVal: appStore.navbar },
    {
      name: 'settings.menu',
      key: 'menu',
      defaultVal: appStore.menu,
    },
    { name: 'settings.footer', key: 'footer', defaultVal: appStore.footer },
    { name: 'settings.tabBar', key: 'tabBar', defaultVal: appStore.tabBar },
    {
      name: 'settings.menuFromServer',
      key: 'menuFromServer',
      defaultVal: appStore.menuFromServer,
    },
    {
      name: 'settings.menuWidth',
      key: 'menuWidth',
      defaultVal: appStore.menuWidth,
      type: 'number',
    },
  ]);
  const othersOpts = computed(() => [
    {
      name: 'settings.colorWeak',
      key: 'colorWeak',
      defaultVal: appStore.colorWeak,
    },
  ]);

  const cancel = () => {
    appStore.updateSettings({ globalSettings: false });
    emit('cancel');
  };
  const loadPrinterSettings = async () => {
    if (!isElectron.value) {
      return;
    }
    printerLoading.value = true;
    try {
      printerSettings.value = await getPrinterSettings();
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '读取打印机设置失败';
      Message.error(message);
    } finally {
      printerLoading.value = false;
    }
  };
  const handleSelectPrinter = async () => {
    printerLoading.value = true;
    try {
      const result = await selectRawPrinter();
      printerSettings.value = result;
      if (result.cancelled) {
        return;
      }
      Message.success('打印机设置已保存');
    } catch (error) {
      const message = error instanceof Error ? error.message : '选择打印机失败';
      Message.error(message);
    } finally {
      printerLoading.value = false;
    }
  };
  const loadZoomSettings = async () => {
    if (!isElectron.value) {
      return;
    }
    const factor = await getZoomFactor();
    zoomPercent.value = Math.round(Number(factor || 1) * 100);
  };
  const handleZoomChange = async (value: unknown) => {
    const rawValue = Array.isArray(value) ? value[0] : value;
    const percent = Number(rawValue || zoomPercent.value || 100);
    const nextPercent = Math.min(Math.max(percent, 50), 150);
    const factor = await setZoomFactor(nextPercent / 100);
    zoomPercent.value = Math.round(Number(factor || 1) * 100);
  };
  const setZoomPercent = (percent: number) => {
    zoomPercent.value = percent;
    handleZoomChange(percent);
  };
  const handleResetData = async () => {
    const password = await openPasswordModal();
    if (!password) {
      return;
    }

    try {
      await verifySystemResetPassword(password);
    } catch {
      return;
    }

    Modal.warning({
      title: '确认数据清零',
      content:
        '该操作不可恢复，将清空单据、货品、商品分类、客户、供应商、报价、盘点、角色权限和收支流水，仅保留老板帐户和基础配置信息。',
      okText: '确认清零',
      cancelText: '取消',
      okButtonProps: {
        status: 'danger',
      },
      async onOk() {
        await resetSystemData(password);
        Message.success('数据已清零');
        cancel();
        window.setTimeout(() => window.location.reload(), 500);
      },
    });
  };
  const copySettings = async () => {
    const text = JSON.stringify(appStore.$state, null, 2);
    await copy(text);
    Message.success(t('settings.copySettings.message'));
  };
  const setVisible = () => {
    appStore.updateSettings({ globalSettings: true });
  };
  watch(visible, (value) => {
    if (value) {
      loadZoomSettings();
      loadPrinterSettings();
    }
  });
</script>

<style scoped lang="less">
  .theme-block {
    margin-bottom: 24px;
  }

  .printer-block {
    margin-bottom: 24px;
  }

  .display-block {
    margin-bottom: 24px;
  }

  .zoom-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .zoom-slider {
    flex: 1;
  }

  .zoom-input {
    width: 76px;
  }

  .zoom-actions {
    margin-top: 8px;
  }

  .theme-title {
    margin: 10px 0;
    padding: 0;
    font-size: 14px;
  }

  .theme-colors {
    display: flex;
    gap: 10px;
  }

  .theme-color {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    padding: 0;
    color: #fff;
    border: 0;
    border-radius: 2px;
    cursor: pointer;

    &:focus-visible {
      outline: 2px solid var(--color-text-4);
      outline-offset: 2px;
    }
  }

  .printer-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-height: 30px;
    gap: 12px;
  }

  .printer-text {
    max-width: 150px;
    overflow: hidden;
    color: var(--color-text-2);
    text-align: right;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .printer-actions {
    margin-top: 10px;
  }

  .data-reset-block {
    padding-top: 14px;
    border-top: 1px solid var(--color-neutral-3);
  }

  .data-reset-description {
    margin: 8px 0 12px;
    color: var(--color-text-3);
    font-size: 12px;
    line-height: 20px;
  }

  .fixed-settings {
    position: fixed;
    top: 280px;
    right: 0;

    svg {
      font-size: 18px;
      vertical-align: -4px;
    }
  }
</style>
