<template>
  <div class="drawer">
    <a-modal
      width="80%"
      :visible="visible"
      unmount-on-close
      :mask-closable="true"
      :ok-loading="loading"
      @cancel="handleCancel"
    >
      <template #title> {{ title }} </template>
      <div>
        <a-form ref="formRef" :model="form" auto-label-width>
          <a-row :gutter="24">
            <a-col :span="10">
              <a-form-item field="orderNo" label="单号:">
                <a-button
                  type="text"
                  size="medium"
                  style="padding: 0"
                  @click="initOrderNo"
                  >{{ form.orderNo }}</a-button
                >
              </a-form-item>
            </a-col>
            <a-col :span="14">
              <a-row :gutter="24">
                <a-col :span="14">
                  <a-form-item
                    field="customerId"
                    label="客户名称"
                    :rules="[{ required: true, message: '请选择' }]"
                  >
                    <a-tree-select
                      v-model="form.customerId"
                      :loading="customerLoading"
                      :data="customerList"
                      placeholder="请选择..."
                      :disabled="form.id != undefined"
                      :field-names="{ key: 'id', title: 'name' }"
                      :filter-tree-node="filterCustomerTreeNode"
                      :allow-search="true"
                      :fallback-option="customerFallback"
                      @popup-visible-change="fetchSupplierData"
                    ></a-tree-select>
                  </a-form-item>
                </a-col>
                <a-col :span="10">
                  <a-form-item field="createTime" label="日期">
                    <a-date-picker
                      v-model="form.createTime"
                      placeholder="请选择"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-col>
          </a-row>
          <a-form-item field="amount" label="收款账户">
            <settler-item-table
              ref="settlerItemTableRef"
              v-model:total-amount="form.amount"
              v-model:order-id="form.id"
            ></settler-item-table>
          </a-form-item>
          <a-form-item field="amount" label="订单结算">
            <purchase-order-table
              ref="purchaseOrderTableRef"
              v-model:customer-id="form.customerId"
              v-model:order-id="form.id"
            ></purchase-order-table>
          </a-form-item>
          <a-form-item field="note" label="备注">
            <a-input
              v-model="form.note"
              placeholder="请输入备注"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item v-if="isRoot" field="status" label="状态">
            <a-select v-model="form.status">
              <a-option :value="0">待审核</a-option>
              <a-option :value="1">正常</a-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>

      <template #footer>
        <div
          style="
            display: flex;
            align-items: center;
            justify-content: space-between;
          "
        >
          <div>
            <a-button v-if="showHistory" type="primary" @click="handleList"
              ><template #icon>
                <icon-history />
              </template>
              <template #default>历史记录</template>
            </a-button>
          </div>
          <div style="display: flex; flex-direction: row; gap: 14px">
            <a-button :loading="exportLoading" @click="handlePrint">
              <template #icon>
                <icon-printer />
              </template>
              <template #default> 打印单据 </template>
            </a-button>
            <a-button @click="handleCancel">取消</a-button>
            <a-button type="primary" :loading="loading" @click="handleOk(0)"
              >保存</a-button
            >
            <a-button
              v-if="form.id === undefined"
              type="primary"
              :loading="loading"
              @click="handleOk(1)"
              >保存并继续</a-button
            >
          </div>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import { Customer } from '@/views/app/customer/types/customer';
  import { list as getCustomerList } from '@/views/app/customer/api/api-customer';
  import SettlerItemTable from '@/views/app/AppReceivePaymentVoucher/components/settler-item-table.vue';
  import { addPrice } from '@/api/common';

  import { useRouter } from 'vue-router';
  import { openPdf } from '@/api/electron/electron-api';
  import type { AppReceivePaymentVoucher } from '../types/AppReceivePaymentVoucher';
  import {
    add,
    edit,
    createOrderNo,
    exportPdfFile,
  } from '../api/api-AppReceivePaymentVoucher';
  import PurchaseOrderTable from './purchase-order-table.vue';

  const exportLoading = ref(false);
  const router = useRouter();
  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const showHistory = defineModel<boolean>('showHistory');
  const customerLoading = ref(false);
  const customerList = ref<Customer[]>([]);
  const settlerItemTableRef = ref<InstanceType<typeof SettlerItemTable> | null>(
    null
  );
  const purchaseOrderTableRef = ref<InstanceType<
    typeof PurchaseOrderTable
  > | null>(null);
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);

  const defaultForm: AppReceivePaymentVoucher = {
    id: undefined,
    orderNo: undefined,
    customerId: undefined,
    status: 1,
    amount: undefined,
    note: undefined,
    createTime: new Date(),
  };
  const form = reactive<AppReceivePaymentVoucher>({ ...defaultForm });
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const initOrderNo = async () => {
    if (form.id === undefined) form.orderNo = (await createOrderNo()).data;
  };

  const showModal = (item: AppReceivePaymentVoucher) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-收款单';
      nextTick(() => {
        settlerItemTableRef.value?.fetchData(item.id?.toString());
        purchaseOrderTableRef.value?.query();
      });
    } else {
      title.value = '新增-收款单';
      initOrderNo();
      nextTick(() => {
        settlerItemTableRef.value?.initData();
        purchaseOrderTableRef.value?.query();
      });
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
  };

  const handleOk = async (state: number) => {
    const items = settlerItemTableRef.value?.getItemsList() || [];
    if (items.length === 0) {
      Message.error('请录入收款账户');
      return;
    }

    const s = await formRef.value.validate();
    if (!s) {
      // 验证通过后可继续操作

      loading.value = true;
      form.settleItems = items;
      let totalAmount = 0;
      form.settleItems.forEach((item) => {
        totalAmount = addPrice(totalAmount, item.amount || 0);
      });
      form.amount = totalAmount;
      form.amountItems = purchaseOrderTableRef.value?.getItemsList();

      console.log('验证通过，提交表单数据:', form);
      try {
        if (!form.id) {
          await add(form);
        } else {
          await edit(form);
        }
      } finally {
        loading.value = false;
      }

      Message.success('操作成功');
      if (state === 0) {
        handleCancel();
      } else {
        Object.assign(form, defaultForm);
        form.orderNo = (await createOrderNo()).data;
        settlerItemTableRef.value?.clearAll();
        purchaseOrderTableRef.value?.clearAll();
      }
      emit('ok', 1);
    }
  };

  const fetchSupplierData = async (e: any) => {
    if (customerList.value.length !== 0) return;

    customerLoading.value = true;
    customerList.value = (await getCustomerList()).data;
    customerLoading.value = false;
  };

  const filterCustomerTreeNode = (searchValue: string, nodeData: Customer) => {
    const t = nodeData.name || '';
    const pyCode = nodeData.pyCode || '';
    const key = searchValue.toLowerCase();
    return t.toLowerCase().indexOf(key) > -1 || pyCode.indexOf(key) > -1;
  };

  const customerFallback = (key: any) => {
    return {
      key: key || '',
      name: form.customerId_dictText || '',
    };
  };
  const handleList = () => {
    router.push('/custom/appReceivePaymentVoucher');
  };

  const handlePrint = async () => {
    const items = settlerItemTableRef.value?.getItemsList();
    if (items && items.length === 0) {
      Message.error('还没有录入帐户信息~');
      return;
    }
    form.settleItems = items;
    exportLoading.value = true;
    try {
      const result = await exportPdfFile(form);
      if (result.data) {
        const apiBaseUrl: string =
          String(import.meta.env.VITE_API_BASE_URL || '')
            .trim()
            .replace(/^['"]|['"]$/g, '') || window.location.origin;
        const { subPath } = result.data;
        const filePath = `${apiBaseUrl}/api/upload/pdf/${subPath}`;
        openPdf(filePath);
      }
    } finally {
      exportLoading.value = false;
    }
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }
</style>
