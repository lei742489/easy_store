<template>
  <a-spin :loading="loading" style="width: 100%">
    <template v-if="menuGroups.length">
      <div class="menu-groups">
        <a-card
          v-for="group in menuGroups"
          :key="group.code"
          class="general-card"
          :header-style="{ paddingBottom: 0 }"
          :body-style="{ paddingTop: '16px' }"
        >
          <template #title>
            <div class="group-title">
              <span class="group-title-mark"></span>
              <span>{{ group.title }}</span>
            </div>
          </template>
          <div class="m-content">
            <div
              v-for="item in group.menus"
              :key="item.id || item.code"
              class="m-item"
              @click="itemClick(item)"
            >
              <a-badge
                class="m-badge"
                :count="getPendingCount(item)"
                :max-count="99"
                :offset="[6, -2]"
              >
                <div class="m-item-body">
                  <img
                    v-if="isImageIcon(item.icon)"
                    class="ico"
                    :src="getImageIcon(item.icon)"
                  />
                  <span v-else class="icon">
                    <component :is="item.icon || 'icon-apps'" />
                  </span>
                  <span class="t1">{{ item.name }}</span>
                </div>
              </a-badge>
            </div>
          </div>
        </a-card>
      </div>
    </template>
    <a-empty v-else description="暂无可用功能" />
  </a-spin>
</template>

<script lang="ts" setup>
  import { computed, onActivated, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { useUserStore } from '@/store';
  import type {
    AppHomeMenu,
    AppHomeMenuGroup,
  } from '@/views/app/AppRole/types/AppRole';
  import {
    listHomeMenus,
    pendingApproveCounts,
  } from '@/views/app/AppRole/api/api-AppRole';

  const icons = import.meta.glob('@/assets/ico/*.png', {
    eager: true,
    import: 'default',
  }) as Record<string, string>;

  const router = useRouter();
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const loading = ref(false);
  const menuGroups = ref<AppHomeMenuGroup[]>([]);
  const pendingCounts = ref<Record<string, number>>({});
  const legacyEntryRoutes: Record<string, string> = {
    SaleOrderModalRef: '/custom/salesOrder/add',
    ReceivePaymentVoucherModal: '/custom/receivePaymentVoucher/add',
    PurchaseOrderModal: '/custom/purchaseOrder/add',
    PaymentVoucherModal: '/custom/paymentVoucher/add',
  };

  const isImageIcon = (icon?: string) => !!icon && icon.endsWith('.png');

  const getImageIcon = (icon?: string) => {
    if (!icon) return '';
    return icons[`/src/assets/ico/${icon}`] || '';
  };

  const getPendingCount = (item: AppHomeMenu) => {
    if (!isRoot.value || !item.code) return 0;
    return pendingCounts.value[item.code] || 0;
  };

  const itemClick = (item: AppHomeMenu) => {
    const targetUrl = item.url || legacyEntryRoutes[item.action || ''];
    if (targetUrl) {
      router.push(targetUrl);
    }
  };

  const fetchMenus = async () => {
    loading.value = true;
    try {
      const menuRequest = listHomeMenus();
      const countRequest = isRoot.value
        ? pendingApproveCounts()
        : Promise.resolve({ data: {} as Record<string, number> });
      const [{ data }, { data: counts }] = await Promise.all([
        menuRequest,
        countRequest,
      ]);
      menuGroups.value = data || [];
      pendingCounts.value = counts || {};
    } finally {
      loading.value = false;
    }
  };

  onMounted(fetchMenus);
  onActivated(fetchMenus);
</script>

<style lang="less" scoped>
  .menu-groups {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }

  .general-card {
    min-width: 0;
    overflow: hidden;
    border: 0;
    border-radius: 8px;
    background: var(--color-bg-2);
    box-shadow: 0 4px 16px rgb(31 35 41 / 8%);
  }

  .group-title {
    display: flex;
    align-items: center;
    color: var(--color-text-1);
    font-size: 16px;
    font-weight: 600;
    line-height: 20px;
  }

  .group-title-mark {
    display: inline-block;
    width: 4px;
    height: 18px;
    margin-right: 10px;
    border-radius: 2px;
    background: rgb(var(--arcoblue-6));
  }

  .m-content {
    display: grid;
    grid-template-columns: repeat(auto-fill, 100px);
    gap: 12px 14px;
    justify-content: start;

    .m-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-width: 0;
      padding: 0 8px;
      height: 96px;
      box-sizing: border-box;
      border: 1px solid #edf0f5;
      border-radius: 8px;
      cursor: pointer;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
      background: var(--color-bg-2);
      box-shadow: 0 1px 5px rgb(31 35 41 / 3%);

      &:hover {
        border-color: rgb(var(--arcoblue-3));
        box-shadow: 0 4px 12px rgb(31 35 41 / 8%);
        transform: scale(1.2);
      }

      .m-badge,
      .m-item-body {
        width: 100%;
        height: 100%;
      }

      .m-badge {
        display: block;
      }

      .m-item-body {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
      }

      .ico,
      .icon {
        width: 38px;
        height: 38px;
      }

      .icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: rgb(var(--arcoblue-6));
        font-size: 29px;
      }

      .t1 {
        margin-top: 10px;
        max-width: 100%;
        overflow: hidden;
        color: var(--color-text-1);
        font-size: 14px;
        line-height: 18px;
        text-align: center;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  @media screen and (max-width: 900px) {
    .menu-groups {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  @media screen and (max-width: 600px) {
    .m-content {
      grid-template-columns: repeat(auto-fill, 140px);
      gap: 10px;
    }

    .m-content .m-item {
      height: 88px;
    }
  }
</style>
