<template>
  <div class="container">
    <a-card class="general-card" title="收支记录" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="form"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="right"
            auto-label-width
            style="margin-top: 10px"
          >
            <a-row :gutter="24">
              <a-col :span="6">
                <a-form-item field="customerId" label="客户">
                  <customer-select
                    v-model:customer-id="form.customerId"
                    placeholder="全部客户"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item field="supplierId" label="供应商">
                  <supplier-select
                    v-model:supplier-id="form.supplierId"
                    placeholder="全部供应商"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item field="fundItem" label="收支项目">
                  <a-select
                    v-model="form.fundItem"
                    :options="filterItemOptions"
                    :loading="filterItemLoading"
                    placeholder="全部项目"
                    allow-clear
                    allow-search
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item field="incomeExpenseType" label="收支类型">
                  <a-select
                    v-model="form.incomeExpenseType"
                    :options="incomeExpenseTypeOptions"
                    placeholder="全部"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item field="businessDate" label="日期">
                  <time-select
                    ref="timeSelectRef"
                    :default-time-idx="2"
                    @change="timeSelectChange"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-col>
        <a-divider style="height: 128px" direction="vertical" />
        <a-col :flex="'86px'" style="text-align: right">
          <a-space direction="vertical" :size="18">
            <a-button type="primary" :loading="loading" @click="search">
              <template #icon><icon-search /></template>
              查询
            </a-button>
            <a-button @click="reset">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      <a-divider style="margin-top: 4px" />
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-space>
            <a-button type="primary" @click="showManualModal">
              <template #icon><icon-plus /></template>
              记帐
            </a-button>
            <a-button :disabled="!records.length" @click="print(true)">
              <template #icon><icon-printer /></template>
              打印
            </a-button>
            <a-button :disabled="!records.length" @click="print(false)">
              预览
            </a-button>
            <a-button :disabled="!records.length" @click="exportCsv">
              <template #icon><icon-download /></template>
              导出
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      <div class="summary-bar">
        <span>期初结存：{{ formatAmount(result.openingBalance) }}</span>
        <span>本期收入：{{ formatAmount(result.incomeTotal) }}</span>
        <span>本期支出：{{ formatAmount(result.expenseTotal) }}</span>
        <span>期末结余：{{ formatAmount(result.endingBalance) }}</span>
      </div>
    </a-card>

    <a-card class="general-card report-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1120, y: getAdaptiveTableScrollY(560) }"
        :row-class="rowClass"
        @sorter-change="onSorterChange"
      >
        <template #operations="{ record }">
          <a-popconfirm
            v-if="canDeleteRecord(record)"
            content="确认删除该条记账记录？"
            @ok="handleDeleteRecord(record)"
          >
            <a-button type="text" size="small" status="danger">删除</a-button>
          </a-popconfirm>
          <span v-else>-</span>
        </template>
      </a-table>
      <div class="pagination-wrap">
        <a-pagination
          :current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          show-page-size
          show-total
          @change="onPageChange"
          @page-size-change="onPageSizeChange"
        />
      </div>
    </a-card>

    <a-modal
      v-model:visible="manualVisible"
      title="记帐"
      width="1060px"
      :ok-loading="manualLoading"
      :mask-closable="false"
      @ok="submitManualRecord"
      @cancel="resetManualForm"
    >
      <a-form ref="manualFormRef" :model="manualForm" auto-label-width>
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item field="flowType" label="记帐类型">
              <a-radio-group
                v-model="manualForm.flowType"
                @change="handleFlowTypeChange"
              >
                <a-radio value="income">记收入</a-radio>
                <a-radio value="expense">记支出</a-radio>
              </a-radio-group>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item
              field="businessDate"
              label="业务日期"
              :rules="[{ required: true, message: '请选择业务日期' }]"
            >
              <a-date-picker
                v-model="manualForm.businessDate"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item
              field="fundItem"
              :label="
                manualForm.flowType === 'income' ? '收入项目' : '支出项目'
              "
              :rules="[{ required: true, message: '请选择收支项目' }]"
            >
              <a-space fill>
                <a-select
                  v-model="manualForm.fundItem"
                  :options="incomeExpenseItems"
                  :field-names="{ value: 'name', label: 'name' }"
                  :loading="itemLoading"
                  :placeholder="
                    manualForm.flowType === 'income'
                      ? '请选择收入项目'
                      : '请选择支出项目'
                  "
                  allow-search
                />
                <a-tooltip content="管理收支项目">
                  <a-button @click="showItemManager">
                    <template #icon><icon-settings /></template>
                  </a-button>
                </a-tooltip>
              </a-space>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item
              field="amount"
              :label="
                manualForm.flowType === 'income' ? '收入金额' : '支出金额'
              "
              :rules="[{ required: true, message: '请输入金额' }]"
            >
              <a-input-number
                v-model="manualForm.amount"
                :min="0.01"
                :precision="2"
                placeholder="0.00"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item field="counterparty" label="往来单位">
              <a-auto-complete
                v-model="manualForm.counterparty"
                :data="counterpartyOptions"
                :filter-option="true"
                placeholder="请输入客户或供应商名称"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item
              field="settleId"
              label="结算账户"
              :rules="[{ required: true, message: '请选择结算账户' }]"
            >
              <a-select
                v-model="manualForm.settleId"
                :options="accountOptions"
                :field-names="{ value: 'id', label: 'name' }"
                :loading="accountLoading"
                placeholder="请选择结算账户"
                allow-search
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item
              field="cashierId"
              label="营业员"
              :rules="[{ required: true, message: '请选择营业员' }]"
            >
              <user-select
                v-model:user-id="manualForm.cashierId"
                placeholder="请选择营业员"
              />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item field="summary" label="说明">
              <a-input
                v-model="manualForm.summary"
                placeholder="请输入业务说明"
                :max-length="200"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <item-manager-modal ref="itemManagerRef" @changed="refreshItems" />
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import { useUserStore } from '@/store';
  import { list as listAccountSettles } from '@/views/app/AppAccountSettle/api/api-AppAccountSettle';
  import type { AppAccountSettle } from '@/views/app/AppAccountSettle/types/AppAccountSettle';
  import SupplierSelect from '@/views/app/AppSupplier/components/SupplierSelectModal.vue';
  import { list as listSuppliers } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import type { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';
  import UserSelect from '@/views/app/AppUser/components/UserSelectModel.vue';
  import CustomerSelect from '@/views/app/customer/components/customer-select-modal.vue';
  import { list as listCustomers } from '@/views/app/customer/api/api-customer';
  import type { Customer } from '@/views/app/customer/types/customer';
  import {
    addIncomeExpenseRecord,
    IncomeExpenseItem,
    IncomeExpenseRecord,
    IncomeExpenseRecordResult,
    listIncomeExpenseItems,
    listIncomeExpenseRecords,
    removeIncomeExpenseRecord,
  } from './api';
  import ItemManagerModal from './components/item-manager-modal.vue';

  const userStore = useUserStore();
  const loading = ref(false);
  const records = ref<IncomeExpenseRecord[]>([]);
  const result = reactive<IncomeExpenseRecordResult>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const manualVisible = ref(false);
  const manualLoading = ref(false);
  const manualFormRef = ref();
  const itemManagerRef = ref<InstanceType<typeof ItemManagerModal> | null>(
    null
  );
  const incomeExpenseItems = ref<IncomeExpenseItem[]>([]);
  const itemLoading = ref(false);
  const filterItemLoading = ref(false);
  const filterItemOptions = ref<Array<{ label: string; value: string }>>([]);
  const accountOptions = ref<AppAccountSettle[]>([]);
  const accountLoading = ref(false);
  const counterpartyOptions = ref<string[]>([]);
  const form = reactive({
    customerId: undefined as number | string | undefined,
    supplierId: undefined as number | string | undefined,
    fundItem: undefined as string | undefined,
    incomeExpenseType: undefined as 'income' | 'expense' | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });
  const businessDateOrder = ref<'asc' | 'desc'>('asc');
  const incomeExpenseTypeOptions = [
    { label: '收入', value: 'income' },
    { label: '支出', value: 'expense' },
  ];
  const manualForm = reactive({
    flowType: 'income' as 'income' | 'expense',
    fundItem: undefined as string | undefined,
    amount: undefined as number | undefined,
    counterparty: '',
    businessDate: dayjs().format('YYYY-MM-DD'),
    settleId: undefined as number | string | undefined,
    cashierId: userStore.id as number | undefined,
    summary: '',
  });

  const formatAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;
  const amountCell = (field: keyof IncomeExpenseRecord) => (record: any) => {
    const row = record.record as IncomeExpenseRecord;
    const value = Number(row[field] || 0);
    let className: string | undefined;
    if (row.isSummary) {
      className = 'summary-amount';
    } else if (value < 0) {
      className = 'danger-amount';
    }
    return h('span', { class: className }, formatAmount(value));
  };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 78, align: 'center' },
    {
      title: '业务日期',
      dataIndex: 'businessDate',
      width: 125,
      align: 'center',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '业务编号',
      dataIndex: 'orderNo',
      width: 180,
      align: 'center',
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '说明',
      dataIndex: 'summary',
      minWidth: 220,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '往来单位',
      dataIndex: 'counterparty',
      width: 170,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '收支项目',
      dataIndex: 'fundItem',
      width: 135,
      align: 'center',
    },
    {
      title: '收入',
      dataIndex: 'income',
      width: 135,
      align: 'right',
      render: amountCell('income'),
    },
    {
      title: '支出',
      dataIndex: 'expense',
      width: 135,
      align: 'right',
      render: amountCell('expense'),
    },
    {
      title: '结余',
      dataIndex: 'balance',
      width: 145,
      align: 'right',
      render: amountCell('balance'),
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 90,
      align: 'center',
    },
  ];

  const tableData = computed<IncomeExpenseRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        businessDate: '',
        orderNo: '',
        summary: '',
        counterparty: '',
        fundItem: '',
        income: result.incomeTotal || 0,
        expense: result.expenseTotal || 0,
        balance: result.endingBalance || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: IncomeExpenseRecord) =>
    record.isSummary ? 'summary-row' : '';

  const canDeleteRecord = (record: IncomeExpenseRecord) =>
    !record.isSummary &&
    String(record.recordType || '') === 'manual' &&
    Boolean(record.recordId);

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listIncomeExpenseRecords({
        ...form,
        businessDateOrder: businessDateOrder.value,
        current: pagination.current,
        pageSize: pagination.pageSize,
      });
      Object.assign(result, data || {});
      records.value = data?.records || [];
      pagination.current = data?.current || 1;
      pagination.total = data?.total || 0;
    } finally {
      loading.value = false;
    }
  };

  const search = async () => {
    pagination.current = 1;
    await fetchData();
  };

  const reset = () => {
    form.customerId = undefined;
    form.supplierId = undefined;
    form.fundItem = undefined;
    form.incomeExpenseType = undefined;
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    businessDateOrder.value = 'asc';
    timeSelectRef.value?.setPreset(2);
    search();
  };

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const onSorterChange = (column: string, order: string) => {
    if (column !== 'businessDate') return;
    businessDateOrder.value = order === 'descend' ? 'desc' : 'asc';
    search();
  };

  const onPageChange = (current: number) => {
    pagination.current = current;
    fetchData();
  };
  const onPageSizeChange = (pageSize: number) => {
    pagination.current = 1;
    pagination.pageSize = pageSize;
    fetchData();
  };
  const fetchIncomeExpenseItems = async () => {
    itemLoading.value = true;
    try {
      const { data } = await listIncomeExpenseItems({
        itemType: manualForm.flowType,
        enabledOnly: true,
      });
      incomeExpenseItems.value = data || [];
    } finally {
      itemLoading.value = false;
    }
  };
  const fetchFilterItems = async () => {
    filterItemLoading.value = true;
    try {
      const { data } = await listIncomeExpenseItems();
      const itemNames = new Set([
        '销售收入',
        '进货支出',
        '收款收入',
        '付款支出',
      ]);
      (data || []).forEach((item: IncomeExpenseItem) => {
        if (item.name) itemNames.add(item.name);
      });
      filterItemOptions.value = Array.from(itemNames).map((name) => ({
        label: name,
        value: name,
      }));
    } finally {
      filterItemLoading.value = false;
    }
  };
  const fetchAccounts = async () => {
    if (accountOptions.value.length) return;
    accountLoading.value = true;
    try {
      const { data } = await listAccountSettles();
      accountOptions.value = data || [];
    } finally {
      accountLoading.value = false;
    }
  };
  const fetchCounterparties = async () => {
    if (counterpartyOptions.value.length) return;
    const [customerResponse, supplierResponse] = await Promise.all([
      listCustomers(),
      listSuppliers(),
    ]);
    const names = [
      ...(customerResponse.data || []).map((item: Customer) => item.name),
      ...(supplierResponse.data || []).map((item: AppSupplier) => item.name),
    ].filter((name): name is string => Boolean(name));
    counterpartyOptions.value = Array.from(new Set(names));
  };
  const resetManualForm = () => {
    manualForm.flowType = 'income';
    manualForm.fundItem = undefined;
    manualForm.amount = undefined;
    manualForm.counterparty = '';
    manualForm.businessDate = dayjs().format('YYYY-MM-DD');
    manualForm.settleId = undefined;
    manualForm.cashierId = userStore.id;
    manualForm.summary = '';
  };
  const handleFlowTypeChange = () => {
    manualForm.fundItem = undefined;
    fetchIncomeExpenseItems();
  };
  const showManualModal = async () => {
    resetManualForm();
    manualVisible.value = true;
    await Promise.all([
      fetchIncomeExpenseItems(),
      fetchAccounts(),
      fetchCounterparties(),
    ]);
  };
  const showItemManager = () => {
    itemManagerRef.value?.showModal();
  };
  const refreshItems = () => {
    fetchIncomeExpenseItems();
  };
  const submitManualRecord = async () => {
    const errors = await manualFormRef.value.validate();
    if (errors) return;
    if (!manualForm.amount || manualForm.amount <= 0) {
      Message.warning('请输入大于 0 的金额');
      return;
    }
    manualLoading.value = true;
    try {
      await addIncomeExpenseRecord({
        settleId: manualForm.settleId,
        cashierId: manualForm.cashierId,
        businessDate: manualForm.businessDate,
        summary: manualForm.summary,
        counterparty: manualForm.counterparty,
        fundItem: manualForm.fundItem,
        income: manualForm.flowType === 'income' ? manualForm.amount : 0,
        expense: manualForm.flowType === 'expense' ? manualForm.amount : 0,
      });
      manualVisible.value = false;
      resetManualForm();
      Message.success({ content: '保存成功', duration: 3000 });
      await search();
    } finally {
      manualLoading.value = false;
    }
  };

  const handleDeleteRecord = async (record: IncomeExpenseRecord) => {
    if (!record.recordId) return;
    try {
      await removeIncomeExpenseRecord(record.recordId);
      Message.success('删除成功');
      await search();
    } catch (error: any) {
      Message.error(error?.message || '删除失败');
    }
  };
  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;
  const exportCsv = () => {
    const headers = [
      '行号',
      '业务日期',
      '业务编号',
      '说明',
      '往来单位',
      '收支项目',
      '收入',
      '支出',
      '结余',
    ];
    const rows = tableData.value.map((item) => [
      item.rowNo,
      item.businessDate,
      item.orderNo,
      item.summary,
      item.counterparty,
      item.fundItem,
      item.income,
      item.expense,
      item.balance,
    ]);
    const blob = new Blob(
      [
        `\uFEFF${[
          headers.join(','),
          ...rows.map((row) => row.map(csvValue).join(',')),
        ].join('\n')}`,
      ],
      { type: 'text/csv;charset=utf-8;' }
    );
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `收支记录_第${pagination.current}页.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  };
  const escapeHtml = (value: unknown) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  const print = (autoPrint: boolean) => {
    if (!tableData.value.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      Message.warning('浏览器阻止了打印窗口，请允许弹窗后重试');
      return;
    }
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.businessDate)}</td>
            <td>${escapeHtml(item.orderNo)}</td>
            <td>${escapeHtml(item.summary)}</td>
            <td>${escapeHtml(item.counterparty)}</td>
            <td>${escapeHtml(item.fundItem)}</td>
            <td class="amount">${escapeHtml(formatAmount(item.income))}</td>
            <td class="amount">${escapeHtml(formatAmount(item.expense))}</td>
            <td class="amount">${escapeHtml(formatAmount(item.balance))}</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>收支记录</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 24px; color: #1d2129; font-family: Arial, "Microsoft YaHei", sans-serif; }
            h1 { margin: 0 0 18px; text-align: center; font-size: 20px; }
            table { width: 100%; border-collapse: collapse; font-size: 12px; }
            th, td { padding: 7px; border: 1px solid #c9cdd4; text-align: center; }
            th { background: #f2f3f5; font-weight: 600; }
            td.amount { text-align: right; white-space: nowrap; }
          </style>
        </head>
        <body>
          <h1>收支记录</h1>
          <table>
            <thead>
              <tr>
                <th>行号</th><th>业务日期</th><th>业务编号</th><th>说明</th>
                <th>往来单位</th><th>收支项目</th><th>收入</th><th>支出</th><th>结余</th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </body>
      </html>
    `);
    printWindow.document.close();
    if (autoPrint) {
      window.setTimeout(() => {
        printWindow.focus();
        printWindow.print();
      }, 100);
    }
  };

  onMounted(() => {
    fetchData();
    fetchFilterItems();
  });
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .general-card {
    margin-bottom: 16px;
  }

  .summary-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    color: var(--color-text-2);

    span {
      white-space: nowrap;
    }
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
  }

  :deep(.summary-row) {
    font-weight: 600;
    background: var(--color-fill-2);
  }

  :deep(.summary-amount) {
    color: rgb(var(--arcoblue-6));
  }

  .danger-amount {
    color: rgb(var(--red-6));
  }
</style>
