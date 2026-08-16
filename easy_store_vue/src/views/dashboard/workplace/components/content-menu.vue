<template>
  <a-spin :loading="loading" style="width: 100%">
    <template v-if="menuGroups.length">
      <a-card
        v-for="group in menuGroups"
        :key="group.code"
        class="general-card"
        :header-style="{ paddingBottom: 0 }"
        :body-style="{ paddingTop: '20px' }"
        :title="group.title"
      >
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
    </template>
    <a-empty v-else description="暂无可用功能" />

    <purchase-order-modal ref="purchaseOrderModalRef" />
    <payment-voucher-modal ref="paymentVoucherModalRef" :show-history="true" />
    <receive-payment-voucher-modal
      ref="receivePaymentVoucherModalRef"
      :show-history="true"
    />
    <sale-order-modal ref="saleOrderModalRef" :show-history="true" />
  </a-spin>
</template>

<script lang="ts" setup>
  import { computed, onActivated, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { useUserStore } from '@/store';
  import PaymentVoucherModal from '@/views/app/AppPaymentVoucher/components/modal.vue';
  import ReceivePaymentVoucherModal from '@/views/app/AppReceivePaymentVoucher/components/modal.vue';
  import SaleOrderModal from '@/views/app/AppSaleOrder/components/modal.vue';
  import PurchaseOrderModal from '@/views/app/AppPurchaseOrder/components/modal.vue';
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
  const purchaseOrderModalRef = ref<InstanceType<
    typeof PurchaseOrderModal
  > | null>(null);
  const paymentVoucherModalRef = ref<InstanceType<
    typeof PaymentVoucherModal
  > | null>(null);
  const receivePaymentVoucherModalRef = ref<InstanceType<
    typeof ReceivePaymentVoucherModal
  > | null>(null);
  const saleOrderModalRef = ref<InstanceType<typeof SaleOrderModal> | null>(
    null
  );

  const isImageIcon = (icon?: string) => !!icon && icon.endsWith('.png');

  const getImageIcon = (icon?: string) => {
    if (!icon) return '';
    return icons[`/src/assets/ico/${icon}`] || '';
  };

  const getPendingCount = (item: AppHomeMenu) => {
    if (!isRoot.value || !item.code) return 0;
    return pendingCounts.value[item.code] || 0;
  };

  const openAction = (action?: string) => {
    if (action === 'PurchaseOrderModal') {
      purchaseOrderModalRef.value?.showModal({});
    } else if (action === 'PaymentVoucherModal') {
      paymentVoucherModalRef.value?.showModal({});
    } else if (action === 'ReceivePaymentVoucherModal') {
      receivePaymentVoucherModalRef.value?.showModal({});
    } else if (action === 'SaleOrderModalRef') {
      saleOrderModalRef.value?.showModal({});
    }
  };

  const itemClick = (item: AppHomeMenu) => {
    if (item.action) {
      openAction(item.action);
      return;
    }
    if (item.url) {
      router.push(item.url);
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
  .general-card {
    margin-bottom: 16px;
  }

  .m-content {
    display: flex;
    flex-direction: row;
    gap: 20px;
    align-items: center;
    flex-wrap: wrap;

    .m-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-width: 90px;
      padding: 0 12px;
      height: 90px;
      background: #f2f3f5;
      border-radius: 4px;
      cursor: pointer;
      transition: transform 0.1s ease, box-shadow 0.1s ease;

      &:hover {
        transform: scale(1.05);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        background: #e7e4f7;
      }

      .m-badge,
      .m-item-body {
        width: 100%;
        height: 100%;
      }

      .m-item-body {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
      }

      .ico,
      .icon {
        width: 37px;
        height: 37px;
      }

      .icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: rgb(var(--arcoblue-6));
        font-size: 30px;
      }

      .t1 {
        font-size: 15px;
        color: #444;
        margin-top: 8px;
      }
    }
  }
</style>
