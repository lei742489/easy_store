<template>
  <div class="drawer">
    <a-modal
      width="82%"
      :visible="visible"
      unmount-on-close
      :mask-closable="true"
      :ok-loading="loading"
      @cancel="handleCancel"
    >
      <template #title>{{ title }}</template>

      <a-tabs v-model:active-key="activeKey">
        <a-tab-pane key="customer" title="按客户添加商品">
          <a-form :model="form" auto-label-width>
            <a-form-item field="customerId" label="客户名称">
              <customer-select
                v-model:customer-id="form.customerId"
                v-model:customer-text="form.customerName"
                placeholder="请选择客户 / 拼音首字母"
              />
            </a-form-item>
            <quote-items-table
              ref="customerTableRef"
              v-model:customer-id="form.customerId"
              v-model:customer-text="form.customerName"
              mode="customer"
            />
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="goods" title="按商品添加客户">
          <a-form :model="form" auto-label-width>
            <a-form-item field="goodsId" label="商品名称">
              <div class="goods-select-line">
                <div class="goods-select-control">
                  <goods-select
                    v-model:goods-id="form.goodsId"
                    v-model:goods-text="form.goodsTitle"
                    placeholder="请选择商品 / 拼音首字母"
                    @change="handleFixedGoodsChange"
                  />
                </div>
                <span v-if="form.goodsId" class="goods-reference">
                  单位：{{ form.unit || '-' }}，零售价：{{
                    formatPlainAmount(form.salePrc)
                  }}，批发价：{{ formatPlainAmount(form.tradePrc) }}
                </span>
              </div>
            </a-form-item>
            <quote-items-table
              ref="goodsTableRef"
              v-model:goods-id="form.goodsId"
              v-model:goods-text="form.goodsTitle"
              mode="goods"
            />
          </a-form>
        </a-tab-pane>
      </a-tabs>

      <template #footer>
        <a-space>
          <a-button @click="handleCancel">取消</a-button>
          <a-button type="primary" :loading="loading" @click="handleOk">
            保存
          </a-button>
        </a-space>
      </template>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { nextTick, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { formatPrice } from '@/api/common';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import CustomerSelect from './customer-select.vue';
  import GoodsSelect from './goods-select.vue';
  import QuoteItemsTable from './quote-items-table.vue';
  import type { AppCustomerQuote } from '../types/AppCustomerQuote';
  import { batchAdd, edit } from '../api/api-AppCustomerQuote';

  type ActiveKey = 'customer' | 'goods';

  const visible = ref(false);
  const title = ref('');
  const loading = ref(false);
  const activeKey = ref<ActiveKey>('customer');
  const customerTableRef = ref<InstanceType<typeof QuoteItemsTable> | null>(
    null
  );
  const goodsTableRef = ref<InstanceType<typeof QuoteItemsTable> | null>(null);

  const defaultForm: AppCustomerQuote = {
    id: undefined,
    customerId: undefined,
    customerName: undefined,
    goodsId: undefined,
    goodsTitle: undefined,
    unit: undefined,
    salePrc: undefined,
    tradePrc: undefined,
    quotePrice: undefined,
    note: undefined,
  };
  const form = reactive<AppCustomerQuote>({ ...defaultForm });

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const resetForm = () => {
    Object.assign(form, defaultForm);
    activeKey.value = 'customer';
  };

  const resetTables = (item?: AppCustomerQuote) => {
    nextTick(() => {
      if (item?.id) {
        customerTableRef.value?.initData([item]);
      } else {
        customerTableRef.value?.clearAll();
      }
      goodsTableRef.value?.clearAll();
    });
  };

  const showModal = (item: AppCustomerQuote) => {
    visible.value = true;
    resetForm();
    if (item.id) {
      title.value = '编辑-大客户报价';
      Object.assign(form, item);
      resetTables(item);
      return;
    }
    title.value = '新增-大客户报价';
    resetTables();
  };

  const handleCancel = () => {
    visible.value = false;
    resetForm();
  };

  const handleFixedGoodsChange = (goods?: GoodsSearchResult) => {
    form.unit = goods?.unit;
    form.salePrc = goods?.salePrc;
    form.tradePrc = goods?.tradePrc;
  };

  const validateFixedSide = () => {
    if (activeKey.value === 'customer' && !form.customerId) {
      Message.error('请选择客户');
      return false;
    }
    if (activeKey.value === 'goods' && !form.goodsId) {
      Message.error('请选择商品');
      return false;
    }
    return true;
  };

  const getActiveItems = () => {
    const tableRef =
      activeKey.value === 'customer'
        ? customerTableRef.value
        : goodsTableRef.value;
    return tableRef?.getItemsList() || [];
  };

  const handleOk = async () => {
    if (!validateFixedSide()) {
      return;
    }

    let items: AppCustomerQuote[] = [];
    try {
      items = getActiveItems();
    } catch {
      return;
    }

    if (items.length === 0) {
      Message.error('请录入报价明细');
      return;
    }
    if (form.id && items.length !== 1) {
      Message.error('编辑报价时只能保留一条明细');
      return;
    }

    loading.value = true;
    try {
      if (form.id) {
        await edit({ ...items[0], id: form.id });
      } else {
        await batchAdd(items);
      }
      Message.success('操作成功');
      handleCancel();
      emit('ok', 1);
    } finally {
      loading.value = false;
    }
  };

  const formatPlainAmount = (value?: number) =>
    value == null ? '-' : formatPrice(value);

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }

  .goods-select-line {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .goods-select-control {
    flex: 0 0 360px;
  }

  .goods-reference {
    color: #1d2129;
    white-space: nowrap;
  }
</style>
