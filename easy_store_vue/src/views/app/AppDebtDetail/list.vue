<template>
  <div class="container">
    <a-card class="general-card" title="欠款明细" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="form"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="center"
            auto-label-width
            style="margin-top: 10px"
          >
            <a-row :gutter="24">
              <a-col :span="6">
                <a-form-item field="customerId" label="客户名称">
                  <customer-select
                    v-model:customer-id="form.customerId"
                    placeholder="请选择"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="18">
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
        <a-divider style="height: 84px" direction="vertical" />
        <a-col :flex="'86px'" style="text-align: right">
          <a-space direction="vertical" :size="18">
            <a-button type="primary" :loading="loading" @click="search">
              <template #icon>
                <icon-search />
              </template>
              查询
            </a-button>
            <a-button @click="reset">
              <template #icon>
                <icon-refresh />
              </template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      <a-divider style="margin-top: 4px" />
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-space>
            <a-button :disabled="!records.length" @click="exportCsv">
              <template #icon>
                <icon-download />
              </template>
              导出
            </a-button>
            <a-button :disabled="!records.length" @click="print">
              <template #icon>
                <icon-printer />
              </template>
              打印
            </a-button>
            <a-button :disabled="!records.length" @click="print">
              预览
            </a-button>
          </a-space>
        </a-col>
      </a-row>
    </a-card>

    <a-card class="general-card report-card" :bordered="false">
      <div class="report-title">
        <span>应收欠款明细</span>
        <template v-if="result.customerName">
          <span class="split">&gt;</span>
          <span>客户 “{{ result.customerName }}” 的明细账</span>
        </template>
      </div>
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 980, y: getAdaptiveTableScrollY(520) }"
        :row-class="rowClass"
      >
        <template #empty>
          <a-empty description="请选择客户后点击统计" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import { exportStyledXls } from '@/utils/styled-xls-export';
  import CustomerSelect from '@/views/app/customer/components/customer-select-modal.vue';
  import { list as getCustomerList } from '@/views/app/customer/api/api-customer';
  import TimeSelect from '@/components/menu/time-select.vue';
  import { DebtDetailRecord, DebtDetailResult, listDebtDetail } from './api';

  const route = useRoute();
  const loading = ref(false);
  const records = ref<DebtDetailRecord[]>([]);
  const result = reactive<DebtDetailResult>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const form = reactive<{
    customerId?: number | string;
    startDate?: string;
    endDate?: string;
  }>({
    customerId: undefined as number | string | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });

  const moneyCell = (field: keyof DebtDetailRecord) => (record: any) => {
    const row = record.record as DebtDetailRecord;
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : undefined },
      getPriceStrByH(Number(row[field] || 0))
    );
  };

  const columns: TableColumnData[] = [
    {
      title: '行号',
      dataIndex: 'rowNo',
      width: 72,
      align: 'center',
    },
    {
      title: '业务日期',
      dataIndex: 'businessDate',
      width: 130,
      align: 'center',
    },
    {
      title: '业务编号',
      dataIndex: 'orderNo',
      width: 190,
      align: 'center',
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      width: 220,
      align: 'center',
    },
    {
      title: '增加应收款',
      dataIndex: 'receivableAmount',
      width: 150,
      align: 'right',
      render: moneyCell('receivableAmount'),
    },
    {
      title: '收回应收款',
      dataIndex: 'receivedAmount',
      width: 150,
      align: 'right',
      render: moneyCell('receivedAmount'),
    },
    {
      title: '期末应收款',
      dataIndex: 'endingBalance',
      width: 160,
      align: 'right',
      render: moneyCell('endingBalance'),
    },
  ];

  const tableData = computed<DebtDetailRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        businessDate: '',
        orderNo: '',
        summary: '',
        receivableAmount: result.receivableTotal || 0,
        receivedAmount: result.receivedTotal || 0,
        endingBalance: result.endingBalance || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: DebtDetailRecord) =>
    record.isSummary ? 'summary-row' : '';

  const resetResult = () => {
    records.value = [];
    result.customerId = undefined;
    result.customerName = undefined;
    result.openingBalance = 0;
    result.receivableTotal = 0;
    result.receivedTotal = 0;
    result.endingBalance = 0;
  };

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const reset = () => {
    form.customerId = undefined;
    timeSelectRef.value?.setPreset(2);
    resetResult();
  };

  const resolveCustomerId = async () => {
    const value = String(form.customerId ?? '').trim();
    if (!value) return undefined;
    if (/^\d+$/.test(value)) return Number(value);

    const { data } = await getCustomerList(value);
    const customers = data || [];
    const customer = customers.find((item) => item.name === value);
    if (customer?.id !== undefined && customer.id !== null) {
      return customer.id;
    }
    return customers.length === 1 ? customers[0].id : undefined;
  };

  const search = async () => {
    if (!form.customerId) {
      resetResult();
      Message.warning('请选择要查询的客户');
      return;
    }
    loading.value = true;
    try {
      const customerId = await resolveCustomerId();
      if (customerId === undefined || customerId === null) {
        resetResult();
        Message.warning('请选择有效的客户');
        return;
      }
      const { data } = await listDebtDetail({
        ...form,
        customerId,
      });
      Object.assign(result, data || {});
      if (data?.customerName) {
        form.customerId = data.customerName;
      }
      records.value = data?.records || [];
    } finally {
      loading.value = false;
    }
  };

  const exportCsv = () => {
    if (!tableData.value.length) return;
    exportStyledXls({
      fileName: `应收欠款明细_${result.customerName || ''}`,
      title: `应收欠款明细 > 客户 “${result.customerName || ''}”`,
      columns: [
        { title: '行号', width: 70 },
        { title: '业务日期', width: 120 },
        { title: '业务编号', width: 180 },
        { title: '摘要', width: 220, align: 'left' },
        { title: '增加应收款', width: 130, align: 'right' },
        { title: '收回应收款', width: 130, align: 'right' },
        { title: '期末应收款', width: 130, align: 'right' },
      ],
      rows: tableData.value.map((item) => [
        item.rowNo,
        item.businessDate,
        item.orderNo,
        item.summary,
        `￥${formatPrice(Number(item.receivableAmount || 0))}`,
        `￥${formatPrice(Number(item.receivedAmount || 0))}`,
        `￥${formatPrice(Number(item.endingBalance || 0))}`,
      ]),
      amountColumnIndexes: [4, 5, 6],
      summaryRowIndexes: tableData.value
        .map((item, index) => (item.isSummary ? index : -1))
        .filter((index) => index >= 0),
    });
  };

  const escapeHtml = (value: unknown) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');

  const formatPrintAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;

  const print = () => {
    if (!tableData.value.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      Message.warning('浏览器阻止了打印窗口，请允许弹窗后重试');
      return;
    }

    const headers = [
      '行号',
      '业务日期',
      '业务编号',
      '摘要',
      '增加应收款',
      '收回应收款',
      '期末应收款',
    ];
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.businessDate)}</td>
            <td>${escapeHtml(item.orderNo)}</td>
            <td>${escapeHtml(item.summary)}</td>
            <td class="amount">${formatPrintAmount(item.receivableAmount)}</td>
            <td class="amount">${formatPrintAmount(item.receivedAmount)}</td>
            <td class="amount">${formatPrintAmount(item.endingBalance)}</td>
          </tr>`
      )
      .join('');

    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>应收欠款明细</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 24px; color: #1d2129; font-family: Arial, "Microsoft YaHei", sans-serif; }
            h1 { margin: 0 0 18px; text-align: center; font-size: 20px; }
            table { width: 100%; border-collapse: collapse; font-size: 13px; }
            th, td { padding: 8px; border: 1px solid #c9cdd4; text-align: center; }
            th { background: #f2f3f5; font-weight: 600; }
            td.amount { text-align: right; }
          </style>
        </head>
        <body>
          <h1>应收欠款明细${
            result.customerName ? ` - ${escapeHtml(result.customerName)}` : ''
          }</h1>
          <table>
            <thead><tr>${headers
              .map((header) => `<th>${header}</th>`)
              .join('')}</tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </body>
      </html>
    `);
    printWindow.document.close();
    window.setTimeout(() => {
      printWindow.focus();
      printWindow.print();
    }, 100);
  };

  onMounted(() => {
    const { customerId, startDate, endDate } = route.query;
    if (typeof startDate === 'string' && typeof endDate === 'string') {
      form.startDate = startDate;
      form.endDate = endDate;
      timeSelectRef.value?.setRange([startDate, endDate]);
    }
    if (typeof customerId === 'string' && customerId) {
      const parsedCustomerId = Number(customerId);
      form.customerId = Number.isNaN(parsedCustomerId)
        ? customerId
        : parsedCustomerId;
      search();
    }
  });
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .general-card {
    margin-bottom: 16px;
  }

  .report-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-1);

    .split {
      color: var(--color-text-3);
      font-weight: 400;
    }
  }

  :deep(.summary-row) {
    font-weight: 600;
    background: var(--color-fill-2);
  }

  :deep(.summary-amount) {
    color: rgb(var(--arcoblue-6));
  }
</style>
