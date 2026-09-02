<template>
  <div class="drawer" :class="{ 'order-entry-page': pageMode }">
    <a-modal
      width="80%"
      :visible="pageMode || visible"
      :mask="!pageMode"
      :closable="!pageMode"
      :render-to-body="!pageMode"
      :modal-class="{ 'order-entry-page-modal': pageMode }"
      unmount-on-close
      :mask-closable="!pageMode"
      :ok-loading="loading"
      @cancel="() => handleCancel()"
    >
      <template #title>
        <div v-if="pageMode" class="order-entry-title">收款单</div>
        <span v-else>{{ title }}</span>
      </template>
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
                  <div class="counterparty-payable">
                    欠款：<span
                      >￥{{ formatPrice(currentCustomerPayable) }}</span
                    >
                    <a-button
                      size="mini"
                      type="outline"
                      :disabled="form.id !== undefined"
                      @click="openCustomerModal"
                    >
                      新增
                    </a-button>
                  </div>
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
          <div class="settlement-section">
            <div class="settlement-section-title">收款账户</div>
            <a-form-item field="amount" class="settlement-form-item">
              <settler-item-table
                ref="settlerItemTableRef"
                v-model:total-amount="form.amount"
                v-model:order-id="form.id"
              ></settler-item-table>
            </a-form-item>
          </div>
          <div class="settlement-section">
            <div class="settlement-section-title">订单结算</div>
            <a-form-item field="amount" class="settlement-form-item">
              <purchase-order-table
                ref="purchaseOrderTableRef"
                v-model:customer-id="form.customerId"
                v-model:order-id="form.id"
              ></purchase-order-table>
            </a-form-item>
          </div>
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
            <a-button type="primary" @click="handleList"
              ><template #icon>
                <icon-history />
              </template>
              <template #default>历史记录</template>
            </a-button>
          </div>
          <div style="display: flex; flex-direction: row; gap: 14px">
            <a-button @click="handleCLodopPrint">
              <template #icon>
                <icon-printer />
              </template>
              <template #default> 打印 </template>
            </a-button>

            <a-button @click="handlePdfPreview">
              <template #icon>
                <icon-file-pdf />
              </template>
              <template #default> PDF预览 </template>
            </a-button>
            <a-button @click="handleReset">重置</a-button>
            <a-button type="primary" :loading="loading" @click="handleOk(1)"
              >保存</a-button
            >
            
          </div>
        </div>
      </template>
    </a-modal>
    <customer-modal ref="customerModalRef" @ok="handleCustomerSaved" />
    <clodop-print-modal ref="clodopPrintModalRef" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import { Customer } from '@/views/app/customer/types/customer';
  import { list as getCustomerList } from '@/views/app/customer/api/api-customer';
  import CustomerModal from '@/views/app/customer/components/customer-modal.vue';
  import SettlerItemTable from '@/views/app/AppReceivePaymentVoucher/components/settler-item-table.vue';
  import { addPrice, formatPrice } from '@/api/common';

  import { useRoute, useRouter } from 'vue-router';
  import { openPdf, rawPrintEscp } from '@/api/electron/electron-api';
  import ClodopPrintModal from '@/components/clodop-print-modal/index.vue';
  import type { AppReceivePaymentVoucher } from '../types/AppReceivePaymentVoucher';
  import {
    add,
    edit,
    createOrderNo,
    exportHtmlFile,
    exportEscpFile,
    exportPdfFile,
  } from '../api/api-AppReceivePaymentVoucher';
  import PurchaseOrderTable from './purchase-order-table.vue';

  defineProps({
    pageMode: {
      type: Boolean,
      default: false,
    },
  });

  const router = useRouter();
  const route = useRoute();
  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const customerLoading = ref(false);
  const customerList = ref<Customer[]>([]);
  const settlerItemTableRef = ref<InstanceType<typeof SettlerItemTable> | null>(
    null
  );
  const purchaseOrderTableRef = ref<InstanceType<
    typeof PurchaseOrderTable
  > | null>(null);
  const customerModalRef = ref<InstanceType<typeof CustomerModal> | null>(null);
  const clodopPrintModalRef = ref<InstanceType<
    typeof ClodopPrintModal
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
  const currentCustomerPayable = computed(() => {
    const customer = customerList.value.find(
      (item) => String(item.id) === String(form.customerId)
    );
    return customer?.payable || 0;
  });
  const loading = ref(false);
  const clodopLoading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', state: number): void;
    (e: 'cancel'): void;
  }>();

  const initOrderNo = async () => {
    if (form.id === undefined) form.orderNo = (await createOrderNo()).data;
  };

  const resetForm = () => {
    Object.keys(form).forEach((key) => {
      delete (form as Record<string, unknown>)[key];
    });
    Object.assign(form, {
      ...defaultForm,
      createTime: new Date(),
    });
    settlerItemTableRef.value?.clearAll();
    purchaseOrderTableRef.value?.clearAll();
    formRef.value?.clearValidate?.();
  };

  const showModal = (item: AppReceivePaymentVoucher) => {
    resetForm();
    visible.value = true;
    if (Object.keys(item).length !== 0) Object.assign(form, item);
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
    void fetchSupplierData();
  };

  const closeForm = (notify = true) => {
    visible.value = false;
    resetForm();
    if (notify) emit('cancel');
  };

  const handleCancel = (notify = true, confirmCancel = true) => {
    if (!confirmCancel) {
      closeForm(notify);
      return;
    }
    Modal.confirm({
      title: '确认取消',
      content: '确认取消并清空当前表单数据吗？',
      onOk: () => closeForm(notify),
    });
  };

  const executeReset = async () => {
    resetForm();
    form.orderNo = (await createOrderNo()).data;
    settlerItemTableRef.value?.initData();
    purchaseOrderTableRef.value?.query();
  };

  const handleReset = () => {
    Modal.confirm({
      title: '确认重置',
      content: '确认清空当前表单数据并重新生成单号吗？',
      onOk: () => executeReset(),
    });
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
        handleCancel(false, false);
      } else {
        resetForm();
        form.orderNo = (await createOrderNo()).data;
      }
      emit('ok', state);
    }
  };

  const selectCustomer = (customer?: Customer) => {
    if (!customer) return;
    form.customerId = String(customer.id);
    form.customerId_dictText = customer.name;
  };

  const findSavedCustomer = (savedItem: Customer) => {
    if (savedItem.id !== undefined && savedItem.id !== null) {
      const item = customerList.value.find(
        (customer) => String(customer.id) === String(savedItem.id)
      );
      if (item) return item;
    }
    const matchedList = customerList.value.filter(
      (customer) => customer.name === savedItem.name
    );
    return matchedList[matchedList.length - 1];
  };

  const reloadCustomerData = async (savedItem?: Customer) => {
    customerLoading.value = true;
    try {
      customerList.value = (await getCustomerList()).data.sort(
        (left, right) => Number(left.id) - Number(right.id)
      );
      if (savedItem) {
        selectCustomer(findSavedCustomer(savedItem));
      }
    } finally {
      customerLoading.value = false;
    }
  };

  const fetchSupplierData = async () => {
    await reloadCustomerData();
  };

  const openCustomerModal = () => {
    customerModalRef.value?.showModal({} as Customer);
  };

  const handleCustomerSaved = async (savedItem: Customer) => {
    await reloadCustomerData(savedItem);
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
    const targetPath = '/custom/appReceivePaymentVoucher';
    if (route.path === targetPath) {
      handleCancel();
      return;
    }
    router.push(targetPath);
  };

  const preparePrintData = () => {
    const items = settlerItemTableRef.value?.getItemsList();
    if (items && items.length === 0) {
      Message.error('还没有录入帐户信息~');
      return false;
    }
    form.settleItems = items;
    return true;
  };

  const buildPdfPath = (result: any) => {
    const apiBaseUrl: string =
      String(import.meta.env.VITE_API_BASE_URL || '')
        .trim()
        .replace(/^['"]|['"]$/g, '') || window.location.origin;
    const { subPath } = result.data;
    return `${apiBaseUrl}/api/upload/pdf/${subPath}`;
  };

  const handlePdfPreview = async () => {
    if (!preparePrintData()) return;
    const result = await exportPdfFile(form);
    if (result.data) {
      openPdf(buildPdfPath(result));
    }
  };

  const handlePrint = async () => {
    if (!preparePrintData()) return;
    if (!window.electronAPI?.rawPrintEscp) {
      await handlePdfPreview();
      return;
    }
    const result = await exportEscpFile(form);
    if (result.data?.data) {
      await rawPrintEscp(result.data.data, result.data.jobName);
      Message.success('打印任务已发送');
    }
  };

  const handleCLodopPrint = async () => {
    if (!preparePrintData()) return;
    clodopLoading.value = true;
    try {
      const result = await exportHtmlFile(form);
      if (!result.data?.html) {
        throw new Error('HTML 文件生成失败');
      }
      clodopPrintModalRef.value?.show({
        htmlContent: result.data.html,
        jobName: result.data.jobName || `收款单_${form.orderNo || ''}`,
      });
    } catch (error) {
      Message.error(error instanceof Error ? error.message : 'C-Lodop 打印失败');
    } finally {
      clodopLoading.value = false;
    }
  };

  defineExpose({ showModal, visible });
</script>

<style lang="less" scoped>
  .drawer {
  }

  .order-entry-page {
    :deep(.arco-modal-container),
    :deep(.arco-modal-wrapper) {
      position: static;
      overflow: visible;
    }

    :deep(.arco-modal) {
      top: 0;
      display: flex;
      flex-direction: column;
      width: 92% !important;
      max-width: 1900px;
      min-height: 70vh;
      margin: 50px auto;
      overflow: hidden;
      border-radius: 4px;
      box-shadow: 0 8px 24px rgb(31 35 41 / 12%);
    }

    :deep(.arco-modal-header) {
      flex: none;
      height: 58px;
      padding: 0;
      border-bottom: 0;
    }

    :deep(.arco-modal-title) {
      width: 100%;
      margin: 0;
      padding: 0;
    }

    :deep(.arco-modal-body) {
      flex: 1;
      min-height: 0;
      padding: 28px 34px 14px;
      overflow-y: auto;

      .arco-input-wrapper,
      .arco-input-number,
      .arco-select-view,
      .arco-picker,
      .arco-textarea-wrapper {
        min-height: 36px;
        background: transparent;
        border: 1px solid var(--color-neutral-3);
        border-radius: 4px;
        box-shadow: none;
      }

      .arco-input-wrapper:hover,
      .arco-input-number:hover,
      .arco-select-view:hover,
      .arco-picker:hover,
      .arco-textarea-wrapper:hover {
        background: transparent;
        border-color: rgb(var(--primary-6));
      }

      .arco-input-wrapper.arco-input-focus,
      .arco-input-number.arco-input-number-focused,
      .arco-select-view.arco-select-view-focus,
      .arco-picker-focused,
      .arco-textarea-wrapper:focus-within {
        background: transparent;
        border-color: rgb(var(--primary-6));
        box-shadow: 0 0 0 1px rgb(var(--primary-6) / 15%);
      }

      .arco-input-number-input,
      .arco-input,
      .arco-textarea {
        background: transparent;
      }
    }

    :deep(.arco-modal-footer) {
      flex: none;
      padding: 14px 20px;
      border-top: 1px solid var(--color-neutral-3);
    }
  }

  .order-entry-title {
    width: 100%;
    height: 58px;
    box-sizing: border-box;
    padding: 0 22px;
    color: var(--color-text-1);
    font-size: 20px;
    font-weight: 600;
    line-height: 58px;
    text-align: center;
    border-bottom: 1px solid var(--color-border-2);
    
  }
  .settlement-section {
    margin: 4px 0 22px;
    padding: 14px;
    background: var(--color-bg-1);
    border-radius: 6px;
    box-shadow: 0 3px 14px rgb(31 35 41 / 8%);
  }

  .settlement-section-title {
    padding-left: 12px;
    color: var(--color-text-1);
    font-weight: 600;
    border-left: 4px solid rgb(var(--primary-6));
  }

  :deep(.settlement-form-item) {
    margin: 12px 0 0;

    .arco-form-item-label-col {
      display: none;
    }

    .arco-form-item-control-wrapper {
      padding-left: 0;
    }
  }

  .counterparty-payable {
    margin-top: -8px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--color-text-2);
    font-size: 13px;
    white-space: nowrap;

    span {
      color: rgb(var(--arcoblue-6));
    }
  }

</style>
