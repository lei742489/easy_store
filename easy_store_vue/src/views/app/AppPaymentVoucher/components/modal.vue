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
            <a-button v-if="showHistory" type="primary" @click="handleList"
              ><template #icon>
                <icon-history />
              </template>
              <template #default>历史记录</template>
            </a-button>
          </div>
          <div style="display: flex; flex-direction: row; gap: 14px">
            <a-button @click="handlePrint">
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
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';
  import { list as getSupplierList } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import SettlerItemTable from '@/views/app/AppPaymentVoucher/components/settler-item-table.vue';
  import { addPrice } from '@/api/common';

  import { useRouter } from 'vue-router';
  import { openPdf, rawPrintEscp } from '@/api/electron/electron-api';
  import { printPdfWithCLodop } from '@/utils/clodop';
  import type { AppPaymentVoucher } from '../types/AppPaymentVoucher';
  import {
    add,
    edit,
    createOrderNo,
    exportEscpFile,
    exportPdfFile,
  } from '../api/api-AppPaymentVoucher';
  import PurchaseOrderTable from './purchase-order-table.vue';

  const router = useRouter();
  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const showHistory = defineModel<boolean>('showHistory');
  const supplierLoading = ref(false);
  const supplierList = ref<AppSupplier[]>([]);
  const settlerItemTableRef = ref<InstanceType<typeof SettlerItemTable> | null>(
    null
  );
  const purchaseOrderTableRef = ref<InstanceType<
    typeof PurchaseOrderTable
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
  const loading = ref(false);
  const clodopLoading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const initOrderNo = async () => {
    if (form.id === undefined) form.orderNo = (await createOrderNo()).data;
  };

  const showModal = (item: AppPaymentVoucher) => {
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

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
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
    if (supplierList.value.length !== 0) return;

    supplierLoading.value = true;
    supplierList.value = (await getSupplierList()).data;
    supplierLoading.value = false;
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
    router.push('/custom/appPaymentVoucher');
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
      const result = await exportPdfFile(form);
      if (!result.data?.subPath) {
        throw new Error('PDF 文件生成失败');
      }
      await printPdfWithCLodop(
        buildPdfPath(result),
        `付款单_${form.orderNo || ''}`
      );
    } catch (error) {
      Message.error(error instanceof Error ? error.message : 'C-Lodop 打印失败');
    } finally {
      clodopLoading.value = false;
    }
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }
</style>
