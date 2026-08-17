<template>
  <div class="drawer">
    <a-modal
      width="90%"
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
                  <a-form-item field="supplierId" label="客户名称">
                    <a-tree-select
                      v-model="form.customerId"
                      :loading="customerLoading"
                      :data="customerList"
                      placeholder="请选择..."
                      :field-names="{ key: 'id', title: 'name' }"
                      :filter-tree-node="filterCustomerTreeNode"
                      :allow-search="true"
                      :fallback-option="customerFallback"
                      @popup-visible-change="fetchSupplierData"
                    ></a-tree-select>
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <div
                    v-if="
                      form.customerId !== undefined && form.customerId !== null
                    "
                    class="counterparty-payable"
                  >
                    欠款：<span
                      >￥{{ formatPrice(currentCustomerPayable) }}</span
                    >
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
                  <a-radio :value="1">销售出货</a-radio>
                  <a-radio :value="2">销售退货</a-radio>
                </a-radio-group>
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item field="createTime" label="日期">
                <a-date-picker v-model="form.createTime" placeholder="请选择" />
              </a-form-item>
            </a-col>
          </a-row>

          <FormItem field="items" label="货品列表">
            <item-table
              ref="itemTableRef"
              v-model:order-type="form.orderType"
              :customer-id="form.customerId"
              @change="updateAmount"
            ></item-table>
          </FormItem>

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
            <a-col :span="5">
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
            <a-col :span="5">
              <a-form-item field="paidAmount" label="实付金额">
                <a-input-number
                  v-model="form.paidAmount"
                  placeholder="0.00"
                  :precision="2"
                  :max-length="10"
                />
              </a-form-item>
            </a-col>
            <a-col :span="5">
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
            <a-col v-if="isRoot" :span="5">
              <a-form-item field="grossProfit" label="销售毛利">
                <a-input-number
                  v-model="form.grossProfit"
                  placeholder="0.00"
                  :disabled="true"
                  :precision="2"
                  :max-length="10"
                />
              </a-form-item>
            </a-col>
            <a-col v-if="isRoot" :span="4">
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
  import { computed, reactive, ref, nextTick, watch } from 'vue';
  import { Message, FormItem } from '@arco-design/web-vue';
  import { useUserStore } from '@/store';
  import { Customer } from '@/views/app/customer/types/customer';
  import { list as getCustomList } from '@/views/app/customer/api/api-customer';
  import { list as getSettleList } from '@/views/app/AppAccountSettle/api/api-AppAccountSettle';
  import { AppAccountSettle } from '@/views/app/AppAccountSettle/types/AppAccountSettle';
  import { list as getUserList } from '@/views/app/AppUser/api/api-AppUser';
  import { AppUser } from '@/views/app/AppUser/types/AppUser';
  import { mulPrice, divPrice, addPrice, formatPrice } from '@/api/common';
import { openPdf, rawPrintEscp } from '@/api/electron/electron-api';
  import { printPdfWithCLodop } from '@/utils/clodop';
  import { useRouter } from 'vue-router';
  import type { AppSaleOrder } from '../types/AppSaleOrder';
  import {
    add,
    edit,
    createOrderNo,
    exportEscpFile,
    exportPdfFile,
  } from '../api/api-AppSaleOrder';
  import ItemTable from './item-table.vue';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const loading = ref(false);
  const clodopLoading = ref(false);
  const customerLoading = ref(false);
  const customerList = ref<Customer[]>([]);
  const settleList = ref<AppAccountSettle[]>([]);
  const cashierList = ref<AppUser[]>([]);
  const itemTableRef = ref<InstanceType<typeof ItemTable> | null>(null);
  const showHistory = defineModel<boolean>('showHistory');
  const router = useRouter();

  const userStore = useUserStore();
  const userInfo = computed(() => {
    return {
      userName: userStore.userName,
      id: userStore.id,
    };
  });
  const isRoot = computed(() => userStore.isRoot === 1);

  const defaultForm: AppSaleOrder = {
    id: undefined,
    orderNo: undefined,
    customerId: undefined,
    customerId_dictText: undefined,
    orderType: 1,
    settleId: 1,
    cashierId: userInfo.value.id,
    status: 1,
    totalAmount: 0,
    payableAmount: undefined,
    paidAmount: undefined,
    freightAmount: undefined,
    grossProfit: undefined,
    discountedAmount: undefined,
    discountRate: 100.0,
    note: undefined,
    createTime: new Date(),
    updateTime: undefined,
    items: undefined,
  };

  const form = reactive<AppSaleOrder>({ ...defaultForm });
  const currentCustomerPayable = computed(() => {
    const customer = customerList.value.find(
      (item) => String(item.id) === String(form.customerId)
    );
    return customer?.payable || 0;
  });
  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const fetchSupplierData = async () => {
    if (customerList.value.length !== 0) return;

    customerLoading.value = true;
    customerList.value = (await getCustomList()).data;
    if (!form.customerId && customerList.value.length > 0) {
      form.customerId = customerList.value[0].id;
    }
    customerLoading.value = false;
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

  const showModal = (item: AppSaleOrder) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-销售单';
    } else {
      title.value = '新增-销售单';
      initOrderNo();
    }
    if (Object.keys(item).length !== 0) Object.assign(form, item);
    fetchSupplierData();

    nextTick(() => {
      setTimeout(() => {
        itemTableRef.value?.initData(item.items || []);
      }, 100);
    });

    fetchSettleList();
    fetchCashierList();
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
  };

  const hasItemWithoutCategory = (items?: AppSaleOrder['items']) => {
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
          handleCancel();
        } else {
          Object.assign(form, defaultForm);
          itemTableRef.value?.clearAll();
          form.orderNo = (await createOrderNo()).data;
        }

        emit('ok', 1);
      }
    }
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
    if (!form.id) {
      form.paidAmount = bc;
    }
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

  function applyCustomerDiscount() {
    const customer = customerList.value.find(
      (item) => String(item.id) === String(form.customerId)
    );
    const discountRate = customer?.discount ?? 100;
    form.discountRate = discountRate;
    updateDiscountAmount();
  }

  watch(
    () => form.customerId,
    () => {
      if (form.customerId !== undefined && form.customerId !== null) {
        applyCustomerDiscount();
      }
    }
  );

  watch(customerList, () => {
    if (form.customerId !== undefined && form.customerId !== null) {
      applyCustomerDiscount();
    }
  });

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
      const result = await exportPdfFile(form);
      if (!result.data?.subPath) {
        throw new Error('PDF 文件生成失败');
      }
      await printPdfWithCLodop(
        buildPdfPath(result),
        `销售单_${form.orderNo || ''}`
      );
    } catch (error) {
      Message.error(error instanceof Error ? error.message : 'C-Lodop 打印失败');
    } finally {
      clodopLoading.value = false;
    }
  };

  const handleList = () => {
    router.push('/custom/salesOrder');
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }

  .counterparty-payable {
    margin-top: 6px;
    color: var(--color-text-2);
    font-size: 13px;
    white-space: nowrap;

    span {
      color: rgb(var(--arcoblue-6));
    }
  }
</style>
