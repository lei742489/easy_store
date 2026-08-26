<template>
  <div class="drawer" :class="{ 'order-entry-page': pageMode }">
    <a-modal
      width="90%"
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
        <div v-if="pageMode" class="order-entry-title">进货单</div>
        <span v-else>{{ title }}</span>
      </template>
      <div>
        <a-form ref="formRef" :model="form" auto-label-width>
          <a-row :gutter="24">
            <a-col :span="12">
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
            <a-col :span="12">
              <a-row :gutter="24">
                <a-col :span="16">
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
                      :field-names="{ key: 'id', title: 'name' }"
                      :filter-tree-node="filterSupplierTreeNode"
                      :allow-search="true"
                      :fallback-option="supplierFallback"
                      @popup-visible-change="fetchSupplierData"
                    ></a-tree-select>
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <div class="counterparty-payable">
                    
                    应付款：<span
                      >￥{{ formatPrice(currentSupplierPayable) }}</span
                    >
                    <a-button style="margin-left: 4rpx;"  size="mini" type="outline" @click="openSupplierModal">
                      新增
                    </a-button>
                  </div>
                  <!--                  <div style="margin-top: 6px; font-size: 13px; color: #69778a"
                    >欠款:￥58555.12</div
                  >-->
                </a-col>
              </a-row>
            </a-col>
          </a-row>
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item field="orderType" label="类型">
                <a-radio-group v-model="form.orderType">
                  <a-radio :value="1">采购进货</a-radio>
                  <a-radio :value="2">采购退货</a-radio>
                </a-radio-group>
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item field="createTime" label="日期">
                <div class="date-code-control">
                  <a-date-picker v-model="form.createTime" placeholder="请选择" />
                  <span>显示货品代码</span>
                  <a-switch v-model="showGoodsCode" size="small">
                    <template #checked>显示</template>
                    <template #unchecked>隐藏</template>
                  </a-switch>
                </div>
              </a-form-item>
            </a-col>
          </a-row>

          <div class="goods-section">
            <div class="goods-section-header">
              <div class="goods-section-title">货品列表</div>
              <div class="goods-section-actions">
                <a-button type="primary" @click="itemTableRef?.addItem()">
                  <template #icon><icon-plus /></template>
                  增加一行
                </a-button>
                <a-popconfirm
                  content="确认清空所有货品数据吗？"
                  @ok="itemTableRef?.clearAll()"
                >
                  <a-button type="primary" status="danger">
                    <template #icon><icon-delete /></template>
                    清空
                  </a-button>
                </a-popconfirm>
              </div>
            </div>
            <FormItem field="items" class="goods-form-item">
              <item-table
                ref="itemTableRef"
                v-model:order-type="form.orderType"
                :show-goods-code="showGoodsCode"
                @change="updateAmount"
              ></item-table>
            </FormItem>
          </div>

          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              :max-length="100"
            />
          </a-form-item>

          <a-row :gutter="24">
            <a-col :span="6"
              ><a-form-item field="discountedAmount" label="折后金额">
                <a-input-number
                  v-model="form.discountedAmount"
                  placeholder="0.00"
                  :precision="2"
                  :max-length="10"
                  @change="updateDiscountRate"
                />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item field="discountRate" label="折扣率">
                <a-input-number
                  v-model="form.discountRate"
                  placeholder="100"
                  :precision="2"
                  :max-length="10"
                  @change="updateDiscountAmount"
                />
                %
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item field="settleId" label="结算账户">
                <a-tree-select
                  v-model="form.settleId"
                  :data="settleList"
                  placeholder="请选择..."
                  :field-names="{ key: 'id', title: 'name' }"
                  :allow-search="true"
                  :allow-clear="true"
                  :fallback-option="settleFallback"
                ></a-tree-select>
              </a-form-item>
            </a-col>
            <a-col v-if="isRoot" :span="6">
              <a-form-item field="cashierId" label="营业员">
                <a-tree-select
                  v-model="form.cashierId"
                  :data="cashierList"
                  placeholder="请选择..."
                  :field-names="{ key: 'id', title: 'realName' }"
                  :allow-search="true"
                  :allow-clear="true"
                  :fallback-option="cashierFallback"
                ></a-tree-select>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="24">
            <a-col :span="6">
              <a-form-item field="payableAmount" label="应付金额">
                <a-input-number
                  v-model="form.payableAmount"
                  placeholder="0.00"
                  :disabled="true"
                  :precision="2"
                  :max-length="10"
                />
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item field="paidAmount" label="实付金额">
                <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
                  <a-input-number
                    v-model="form.paidAmount"
                    placeholder="0.00"
                    :precision="2"
                    :max-length="10"
                    style="flex: 1; min-width: 0;"
                  />
                  <a-button size="mini" type="outline" @click="fillPaidAmount">
                    已付
                  </a-button>
                </div>
              </a-form-item>
            </a-col>
            <a-col :span="6">
              <a-form-item field="freightAmount" label="运费">
                <a-input-number
                  v-model="form.freightAmount"
                  placeholder="0.00"
                  :precision="2"
                  :max-length="10"
                  @change="updatePayableAmount"
                />
              </a-form-item>
            </a-col>
            <a-col v-if="isRoot" :span="6">
              <a-form-item field="status" label="状态">
                <a-select v-model="form.status">
                  <a-option :value="0">待审核</a-option>
                  <a-option :value="1">正常</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </div>

      <!-- modal 内容 -->
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
  import { computed, reactive, ref, nextTick } from 'vue';
  import { Message, Modal, FormItem } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';
  import { list as getSupplierList } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import SupplierModal from '@/views/app/AppSupplier/components/modal.vue';
  import { list as getSettleList } from '@/views/app/AppAccountSettle/api/api-AppAccountSettle';
  import { AppAccountSettle } from '@/views/app/AppAccountSettle/types/AppAccountSettle';
  import { list as getUserList } from '@/views/app/AppUser/api/api-AppUser';
  import { AppUser } from '@/views/app/AppUser/types/AppUser';
  import { mulPrice, divPrice, addPrice, formatPrice } from '@/api/common';
  import { openPdf, rawPrintEscp } from '@/api/electron/electron-api';
  import ClodopPrintModal from '@/components/clodop-print-modal/index.vue';
  import { useRoute, useRouter } from 'vue-router';
  import type { AppPurchaseOrder } from '../types/AppPurchaseOrder';
  import {
    add,
    edit,
    createOrderNo,
    exportHtmlFile,
    exportEscpFile,
    exportPdfFile,
  } from '../api/api-AppPurchaseOrder';
  import ItemTable from './item-table.vue';

  defineProps({
    pageMode: {
      type: Boolean,
      default: false,
    },
  });

  const visible = ref(false);
  const formRef = ref();
  const showGoodsCode = ref(true);
  const title = ref('');
  const loading = ref(false);
  const clodopLoading = ref(false);
  const supplierLoading = ref(false);
  const supplierList = ref<AppSupplier[]>([]);
  const settleList = ref<AppAccountSettle[]>([]);
  const cashierList = ref<AppUser[]>([]);
  const itemTableRef = ref<InstanceType<typeof ItemTable> | null>(null);
  const supplierModalRef = ref<InstanceType<typeof SupplierModal> | null>(null);
  const clodopPrintModalRef = ref<InstanceType<
    typeof ClodopPrintModal
  > | null>(null);
  const router = useRouter();
  const route = useRoute();

  const userStore = useUserStore();
  const userInfo = computed(() => {
    return {
      userName: userStore.userName,
      id: userStore.id,
    };
  });
  const isRoot = computed(() => userStore.isRoot === 1);

  const defaultForm: AppPurchaseOrder = {
    id: undefined,
    orderNo: undefined,
    supplierId: undefined,
    orderType: 1,
    settleId: 1,
    cashierId: userInfo.value.id,
    status: 1,
    totalAmount: 0,
    payableAmount: undefined,
    paidAmount: 0,
    freightAmount: undefined,
    discountedAmount: undefined,
    discountRate: 100.0,
    note: undefined,
    createTime: new Date(),
    updateTime: undefined,
    items: undefined,
  };

  const form = reactive<AppPurchaseOrder>({ ...defaultForm });
  const currentSupplierPayable = computed(() => {
    const supplier = supplierList.value.find(
      (item) => String(item.id) === String(form.supplierId)
    );
    return supplier?.payable || 0;
  });
  const emit = defineEmits<{
    (e: 'ok', state: number): void;
    (e: 'cancel'): void;
  }>();

  const selectDefaultSupplier = () => {
    if (
      form.id === undefined &&
      (form.supplierId === undefined || form.supplierId === null) &&
      supplierList.value.length > 0
    ) {
      form.supplierId = supplierList.value[0].id;
    }
  };

  const selectSupplier = (supplier?: AppSupplier) => {
    if (!supplier) return;
    form.supplierId = supplier.id;
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
      } else {
        selectDefaultSupplier();
      }
    } finally {
      supplierLoading.value = false;
    }
  };

  const fetchSupplierData = async () => {
    if (supplierList.value.length === 0) {
      await reloadSupplierData();
    }
    selectDefaultSupplier();
  };

  const openSupplierModal = () => {
    supplierModalRef.value?.showModal({} as AppSupplier);
  };

  const handleSupplierSaved = async (savedItem: AppSupplier) => {
    await reloadSupplierData(savedItem);
  };

  const fetchCashierList = async () => {
    if (!isRoot.value) return;
    cashierList.value = (await getUserList()).data;
  };

  const fetchSettleList = async () => {
    settleList.value = (await getSettleList()).data;
  };

  const initOrderNo = async () => {
    if (form.id === undefined) form.orderNo = (await createOrderNo()).data;
  };

  const resetForm = () => {
    Object.keys(form).forEach((key) => {
      delete (form as Record<string, unknown>)[key];
    });
    Object.assign(form, {
      ...defaultForm,
      cashierId: userInfo.value.id,
      createTime: new Date(),
    });
    itemTableRef.value?.clearAll();
    formRef.value?.clearValidate?.();
  };

  const showModal = (item: AppPurchaseOrder) => {
    resetForm();
    visible.value = true;
    if (item.id) {
      title.value = '编辑-进货单';
    } else {
      title.value = '新增-进货单';
      initOrderNo();
    }
    if (Object.keys(item).length !== 0) {
      Object.assign(form, item);
      if (form.supplierId !== undefined && form.supplierId !== null) {
        const numericSupplierId = Number(form.supplierId);
        if (!Number.isNaN(numericSupplierId)) {
          form.supplierId = numericSupplierId;
        }
      }
    }
    fetchSupplierData();

    nextTick(() => {
      setTimeout(() => {
        itemTableRef.value?.initData(item.items || []);
      }, 100);
    });

    fetchSettleList();
    fetchCashierList();
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
    selectDefaultSupplier();
    form.orderNo = (await createOrderNo()).data;
  };

  const handleReset = () => {
    Modal.confirm({
      title: '确认重置',
      content: '确认清空当前表单数据并重新生成单号吗？',
      onOk: () => executeReset(),
    });
  };

  const hasItemWithoutCategory = (items?: AppPurchaseOrder['items']) => {
    return items?.some(
      (row) =>
        row.categoryId === undefined ||
        row.categoryId === null ||
        Number.isNaN(Number(row.categoryId)) ||
        Number(row.categoryId) <= 0
    );
  };

  const handleOk = async (state: number) => {
    const items = itemTableRef.value?.getItemsList();
    if (items && items.length === 0) {
      Message.error('您还没有录入货品哦~');
    } else if (hasItemWithoutCategory(items)) {
      Message.error('所有商品必须选择分类');
    } else {
      const s = await formRef.value.validate();
      if (!s) {
        // 验证通过后可继续操作

        loading.value = true;
        form.items = items;
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
          selectDefaultSupplier();
          form.orderNo = (await createOrderNo()).data;
        }

      emit('ok', state);
      }
    }
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

  const settleFallback = (key: any) => {
    return {
      key: key || '',
      name: form.settleId_dictText || '',
    };
  };

  const cashierFallback = (key: any) => {
    return {
      key: key || '',
      realName: form.cashierId_dictText || '',
    };
  };

  const updatePayableAmount = () => {
    const rate: number = mulPrice(form.discountRate || 0, 0.01);
    const ac = mulPrice(form.totalAmount || 0, rate);
    const bc = addPrice(ac, form.freightAmount || 0);
    form.payableAmount = bc;
  };

  const fillPaidAmount = () => {
    form.paidAmount = form.payableAmount || 0;
  };

  const updateDiscountRate = () => {
    const rate: number = divPrice(
      form.discountedAmount || 0,
      form.totalAmount || 0
    );
    form.discountRate = mulPrice(rate, 100);
    updatePayableAmount();
  };

  const updateDiscountAmount = () => {
    const rate: number = mulPrice(form.discountRate || 0, 0.01);
    form.discountedAmount = mulPrice(form.totalAmount || 0, rate);
    updatePayableAmount();
  };

  const updateAmount = (amount: number) => {
    form.totalAmount = amount;
    updatePayableAmount();
    updateDiscountAmount();
  };

  const preparePrintData = () => {
    const items = itemTableRef.value?.getItemsList();
    if (items && items.length === 0) {
      Message.error('您还没有录入货品哦~');
      return false;
    }
    if (hasItemWithoutCategory(items)) {
      Message.error('所有商品必须选择分类');
      return false;
    }
    form.items = items;
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
        jobName: result.data.jobName || `进货单_${form.orderNo || ''}`,
      });
    } catch (error) {
      Message.error(error instanceof Error ? error.message : 'C-Lodop 打印失败');
    } finally {
      clodopLoading.value = false;
    }
  };

  const handleList = () => {
    const targetPath = '/custom/appSaleOrder';
    if (route.path === targetPath) {
      handleCancel();
      return;
    }
    router.push(targetPath);
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
    color: #fff;
    font-size: 20px;
    font-weight: 600;
    line-height: 58px;
    text-align: center;
    background: linear-gradient(
      135deg,
      rgb(var(--primary-5)) 0%,
      rgb(var(--primary-6)) 72%,
      rgb(var(--primary-7)) 100%
    );
  }

  .date-code-control {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;

    > span {
      margin-left: auto;
      color: var(--color-text-2);
      white-space: nowrap;
    }
  }

  .goods-section {
    margin: 4px 0 22px;
    padding: 14px;
    background: var(--color-bg-1);
    border-radius: 6px;
    box-shadow: 0 3px 14px rgb(31 35 41 / 8%);
  }

  .goods-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .goods-section-title {
    padding-left: 12px;
    color: var(--color-text-1);
    font-weight: 600;
    border-left: 4px solid rgb(var(--primary-6));
  }

  .goods-section-actions {
    display: flex;
    gap: 10px;
  }

  :deep(.goods-form-item) {
    margin-bottom: 0;

    .arco-form-item-label-col {
      display: none;
    }

    .arco-form-item-control-wrapper {
      padding-left: 0;
    }
  }

  .counterparty-payable {
    margin-top: 6px;
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
