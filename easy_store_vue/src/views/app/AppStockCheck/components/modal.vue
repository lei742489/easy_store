<template>
  <a-modal
    width="90%"
    :visible="visible"
    unmount-on-close
    :mask-closable="true"
    :ok-loading="loading"
    @cancel="handleCancel"
  >
    <template #title>{{ title }}</template>
    <a-form ref="formRef" :model="form" auto-label-width>
      <a-row :gutter="24">
        <a-col :span="12">
          <a-form-item field="orderNo" label="单号">
            <a-button
              type="text"
              size="medium"
              style="padding: 0"
              @click="initOrderNo"
            >
              {{ form.orderNo }}
            </a-button>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item field="createTime" label="单据日期">
            <a-date-picker v-model="form.createTime" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item field="items" label="盘点商品">
        <item-table ref="itemTableRef" />
      </a-form-item>

      <a-row :gutter="24">
        <a-col :span="isRoot ? 18 : 24">
          <a-form-item field="note" label="说明">
            <a-textarea v-model="form.note" :max-length="100" />
          </a-form-item>
        </a-col>
        <a-col v-if="isRoot" :span="6">
          <a-form-item field="cashierId" label="营业员">
            <user-select
              v-model:user-id="form.cashierId"
              placeholder="请选择营业员"
            />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="handleCancel">取消</a-button>
        <a-button type="primary" :loading="loading" @click="handleOk(false)">
          保存
        </a-button>
        <a-button
          v-if="form.id === undefined"
          type="primary"
          :loading="loading"
          @click="handleOk(true)"
        >
          保存并继续
        </a-button>
      </a-space>
    </template>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import UserSelect from '@/views/app/AppUser/components/UserSelectModel.vue';
  import { add, createOrderNo, edit } from '../api/api-AppStockCheck';
  import type { AppStockCheck } from '../types/AppStockCheck';
  import type { AppStockCheckItem } from '../types/AppStockCheckItem';
  import ItemTable from './item-table.vue';

  const visible = ref(false);
  const loading = ref(false);
  const title = ref('');
  const formRef = ref();
  const itemTableRef = ref<InstanceType<typeof ItemTable> | null>(null);
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);

  const createDefaultForm = (): AppStockCheck => ({
    id: undefined,
    orderNo: undefined,
    cashierId: userStore.id,
    cashierName: undefined,
    profitLossQuantity: 0,
    profitLossAmount: 0,
    note: undefined,
    createTime: new Date(),
    updateTime: undefined,
    items: undefined,
  });

  const form = reactive<AppStockCheck>(createDefaultForm());
  const emit = defineEmits<{
    (event: 'ok'): void;
  }>();

  const initOrderNo = async () => {
    if (form.id === undefined) {
      form.orderNo = (await createOrderNo()).data;
    }
  };

  const resetForm = () => {
    Object.assign(form, createDefaultForm());
    itemTableRef.value?.clearAll();
  };

  const showModal = (record: AppStockCheck) => {
    visible.value = true;
    if (record.id) {
      title.value = '编辑-盘点单';
      Object.assign(form, record);
      if (form.cashierId !== undefined && form.cashierId !== null) {
        const cashierId = Number(form.cashierId);
        if (!Number.isNaN(cashierId)) {
          form.cashierId = cashierId;
        }
      }
    } else {
      title.value = '新增-盘点单';
      resetForm();
      initOrderNo();
    }
    nextTick(() => {
      itemTableRef.value?.initData(record.items || []);
    });
  };

  const handleCancel = () => {
    visible.value = false;
    resetForm();
  };

  const getItems = () =>
    (itemTableRef.value?.getItemsList() || []) as AppStockCheckItem[];

  const handleOk = async (continueAdding: boolean) => {
    const items = getItems();
    if (!items.length) {
      Message.error('请至少录入一项盘点商品');
      return;
    }
    const error = await formRef.value?.validate();
    if (error) return;

    loading.value = true;
    form.items = items;
    try {
      if (form.id === undefined) {
        await add(form);
      } else {
        await edit(form);
      }
      Message.success('操作成功');
      emit('ok');
      if (continueAdding) {
        resetForm();
        await initOrderNo();
      } else {
        handleCancel();
      }
    } finally {
      loading.value = false;
    }
  };

  defineExpose({ showModal });
</script>
