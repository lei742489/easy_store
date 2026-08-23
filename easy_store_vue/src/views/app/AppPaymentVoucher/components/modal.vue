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
                    field="supplierId"
                    label="供应商"
                    :rules="[{ required: true, message: '请选择供应商' }]"
                  >
                    <a-tree-select
                      v-model="form.supplierId"
                      :loading="supplierLoading"
                      :data="supplierList"
                      placeholder="请选择..."
                      :disabled="form.id != undefined"
                      :field-names="{ key: 'id', title: 'name' }"
                      :filter-tree-node="filterSupplierTreeNode"
                      :allow-search="true"
                      :fallback-option="supplierFallback"
                      @popup-visible-change="fetchSupplierData"
                    ></a-tree-select>
                  </a-form-item>
                  <div class="counterparty-payable">
                    应付款：<span
                      >￥{{ formatPrice(currentSupplierPayable) }}</span
                    >
                    <a-button
                      size="mini"
                      type="outline"
                      :disabled="form.id !== undefined"
                      @click="openSupplierModal"
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
          <a-form-item field="amount" label="付款账户">
            <settler-item-table
              ref="settlerItemTableRef"
              v-model:total-amount="form.amount"
              v-model:order-id="form.id"
            ></settler-item-table>
          </a-form-item>
          <a-form-item field="amount" label="订单结算">
            <purchase-order-table
              ref="purchaseOrderTableRef"
              v-model:supplier-id="form.supplierId"
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
    <supplier-modal ref="supplierModalRef" @ok="handleSupplierSaved" />
    <clodop-print-modal ref="clodopPrintModalRef" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';
  import { list as getSupplierList } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import SupplierModal from '@/views/app/AppSupplier/components/modal.vue';
  import SettlerItemTable from '@/views/app/AppPaymentVoucher/components/settler-item-table.vue';
  import { addPrice, formatPrice } from '@/api/common';

  import { useRoute, useRouter } from 'vue-router';
  import { openPdf, rawPrintEscp } from '@/api/electron/electron-api';
  import ClodopPrintModal from '@/components/clodop-print-modal/index.vue';
  import type { AppPaymentVoucher } from '../types/AppPaymentVoucher';
  import {
    add,
    edit,
    createOrderNo,
    exportHtmlFile,
    exportEscpFile,
    exportPdfFile,
  } from '../api/api-AppPaymentVoucher';
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
  const supplierLoading = ref(false);
  const supplierList = ref<AppSupplier[]>([]);
  const settlerItemTableRef = ref<InstanceType<typeof SettlerItemTable> | null>(
    null
  );
  const purchaseOrderTableRef = ref<InstanceType<
    typeof PurchaseOrderTable
  > | null>(null);
  const supplierModalRef = ref<InstanceType<typeof SupplierModal> | null>(null);
  const clodopPrintModalRef = ref<InstanceType<
    typeof ClodopPrintModal
  > | null>(null);
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);

  const defaultForm: AppPaymentVoucher = {
    id: undefined,
    orderNo: undefined,
    supplierId: undefined,
    status: 1,
    amount: undefined,
    note: undefined,
    createTime: new Date(),
  };
  const form = reactive<AppPaymentVoucher>({ ...defaultForm });
  const currentSupplierPayable = computed(() => {
    const supplier = supplierList.value.find(
      (item) => String(item.id) === String(form.supplierId)
    );
    return supplier?.payable || 0;
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

  const showModal = (item: AppPaymentVoucher) => {
    resetForm();
    visible.value = true;
    if (item.id) {
      title.value = '编辑-付款单';
      nextTick(() => {
        settlerItemTableRef.value?.fetchData(item.id?.toString());
        purchaseOrderTableRef.value?.query();
      });
    } else {
      title.value = '新增-付款单';
      initOrderNo();
      nextTick(() => {
        settlerItemTableRef.value?.initData();
      });
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
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
      Message.error('请录入付款账户');
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

  const selectSupplier = (supplier?: AppSupplier) => {
    if (!supplier) return;
    form.supplierId = String(supplier.id);
    form.supplierId_dictText = supplier.name;
  };

  const findSavedSupplier = (savedItem: AppSupplier) => {
    if (savedItem.id !== undefined && savedItem.id !== null) {
      const item = supplierList.value.find(
        (supplier) => String(supplier.id) === String(savedItem.id)
      );
      if (item) return item;
    }
    const matchedList = supplierList.value.filter(
      (supplier) => supplier.name === savedItem.name
    );
    return matchedList[matchedList.length - 1];
  };

  const reloadSupplierData = async (savedItem?: AppSupplier) => {
    supplierLoading.value = true;
    try {
      supplierList.value = (await getSupplierList()).data.sort(
        (left, right) => Number(left.id) - Number(right.id)
      );
      if (savedItem) {
        selectSupplier(findSavedSupplier(savedItem));
      }
    } finally {
      supplierLoading.value = false;
    }
  };

  const fetchSupplierData = async () => {
    if (supplierList.value.length !== 0) return;
    await reloadSupplierData();
  };

  const openSupplierModal = () => {
    supplierModalRef.value?.showModal({} as AppSupplier);
  };

  const handleSupplierSaved = async (savedItem: AppSupplier) => {
    await reloadSupplierData(savedItem);
  };

  const filterSupplierTreeNode = (
    searchValue: string,
    nodeData: AppSupplier
  ) => {
    const t = nodeData.name || '';
    const pyCode = nodeData.pyCode || '';
    const key = searchValue.toLowerCase();
    return t.toLowerCase().indexOf(key) > -1 || pyCode.indexOf(key) > -1;
  };

  const supplierFallback = (key: any) => {
    return {
      key: key || '',
      name: form.supplierId_dictText || '',
    };
  };
  const handleList = () => {
    const targetPath = '/custom/appPaymentVoucher';
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
        jobName: result.data.jobName || `付款单_${form.orderNo || ''}`,
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

  :global(.order-entry-page-modal) {
    box-shadow: var(--shadow2-center);
  }

  &.order-entry-page {
    :deep(.arco-modal-container),
    :deep(.arco-modal-wrapper) {
      position: static;
      overflow: visible;
    }

    :deep(.arco-modal) {
      top: 0;
      margin: 24px auto;
    }
  }
</style>
