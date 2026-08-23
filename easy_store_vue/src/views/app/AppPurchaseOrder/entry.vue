<template>
  <form-modal
    ref="formModalRef"
    page-mode
    @ok="handleSaved"
    @cancel="returnToList"
  />
</template>

<script lang="ts" setup>
  import { nextTick, onActivated, onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import type { AppPurchaseOrder } from './types/AppPurchaseOrder';
  import FormModal from './components/modal.vue';

  const route = useRoute();
  const router = useRouter();
  const formModalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const lastOpenKey = ref('');

  const returnToList = () => {
    lastOpenKey.value = '';
    router.replace({ name: 'AppSaleOrder' });
  };

  const handleSaved = (state: number) => {
    if (state === 0) {
      returnToList();
    }
  };

  const getEditItem = (): AppPurchaseOrder => {
    const editItem = window.history.state?.editItem;
    if (typeof editItem !== 'string') return {};
    try {
      return JSON.parse(editItem) as AppPurchaseOrder;
    } catch {
      return {};
    }
  };

  const openNewOrder = async (force = false) => {
    await nextTick();
    const openKey = `${route.fullPath}:${window.history.state?.openAt || ''}`;
    if (!force && lastOpenKey.value === openKey) return;
    lastOpenKey.value = openKey;
    formModalRef.value?.showModal(getEditItem());
  };

  onMounted(() => openNewOrder(true));
  onActivated(() => openNewOrder());
  watch(
    () => route.query.refreshAt,
    () => openNewOrder(true)
  );
</script>

<script lang="ts">
  export default {
    name: 'PurchaseOrderAdd',
  };
</script>
