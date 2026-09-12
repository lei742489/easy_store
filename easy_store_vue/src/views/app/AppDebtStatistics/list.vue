<template>
  <div class="container">
    <a-card class="general-card" title="应收欠款报告" style="margin-top: 12px">
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
                <a-form-item field="customerId" label="客户">
                  <customer-select
                    v-model:customer-id="form.customerId"
                    placeholder="全部客户"
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
              统计
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
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1120, y: getAdaptiveTableScrollY(560) }"
        :row-class="rowClass"
      >
        <template #empty>
          <a-empty description="暂无欠款客户" />
        </template>


        <template #operations="{ record }" >
          <a-button type="text" size="small" @click="goDetail(record)" v-if="record.customerName">
            明细</a-button
          >
         
        </template> 
      </a-table>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import { exportStyledXls } from '@/utils/styled-xls-export';
  import CustomerSelect from '@/views/app/customer/components/customer-select-modal.vue';
  import { list as getCustomerList } from '@/views/app/customer/api/api-customer';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    DebtStatisticsRecord,
    DebtStatisticsResult,
    listDebtStatistics,
  } from './api';

  const router = useRouter();
  const loading = ref(false);
  const records = ref<DebtStatisticsRecord[]>([]);
  const result = reactive<DebtStatisticsResult>({});
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

  const amountClass = (value?: number) => {
    if (Number(value || 0) < 0) return 'danger-amount';
    return undefined;
  };

  const moneyCell = (field: keyof DebtStatisticsRecord) => (record: any) => {
    const row = record.record as DebtStatisticsRecord;
    const value = Number(row[field] || 0);
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : amountClass(value) },
      getPriceStrByH(value)
    );
  };

  const goDetail = (record: DebtStatisticsRecord) => {
    if (!record.customerId || record.isSummary) return;
    router.push({
      path: '/custom/debtDetail',
      query: {
        customerId: record.customerId,
        startDate: form.startDate,
        endDate: form.endDate,
      },
    });
  };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      width: 260,
      align: 'left',
    },
    {
      title: '期初应收款',
      dataIndex: 'openingBalance',
      width: 160,
      align: 'right',
      render: moneyCell('openingBalance'),
    },
    {
      title: '增加应收款',
      dataIndex: 'receivableAmount',
      width: 160,
      align: 'right',
      render: moneyCell('receivableAmount'),
    },
    {
      title: '收回应收款',
      dataIndex: 'receivedAmount',
      width: 160,
      align: 'right',
      render: moneyCell('receivedAmount'),
    },
    { title: '抹零', dataIndex: 'roundingAmount', width: 120, align: 'right' },
    {
      title: '期末应收款',
      dataIndex: 'endingBalance',
      width: 160,
      align: 'right',
      render: moneyCell('endingBalance'),
    },
    {
      title: '操作',
      dataIndex: 'operation',
      slotName: 'operations',
      width: 90,
      align: 'center'
    },
  ];

  const tableData = computed<DebtStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        customerName: '',
        openingBalance: result.openingTotal || 0,
        receivableAmount: result.receivableTotal || 0,
        receivedAmount: result.receivedTotal || 0,
        roundingAmount: '',
        endingBalance: result.endingTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: DebtStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
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
    loading.value = true;
    try {
      const customerId = form.customerId
        ? await resolveCustomerId()
        : undefined;
      if (
        form.customerId &&
        (customerId === undefined || customerId === null)
      ) {
        records.value = [];
        Object.assign(result, {
          openingTotal: 0,
          receivableTotal: 0,
          receivedTotal: 0,
          endingTotal: 0,
        });
        Message.warning('请选择有效的客户');
        return;
      }
      const { data } = await listDebtStatistics({ ...form, customerId });
      Object.assign(result, data || {});
      records.value = data?.records || [];
    } finally {
      loading.value = false;
    }
  };

  const reset = () => {
    form.customerId = undefined;
    timeSelectRef.value?.setPreset(2);
    search();
  };

  const exportCsv = () => {
    if (!tableData.value.length) return;
    exportStyledXls({
      fileName: '应收欠款报告',
      title: '应收欠款报告',
      columns: [
        { title: '行号', width: 70 },
        { title: '客户名称', width: 240, align: 'left' },
        { title: '期初应收款', width: 130, align: 'right' },
        { title: '增加应收款', width: 130, align: 'right' },
        { title: '收回应收款', width: 130, align: 'right' },
        { title: '抹零', width: 100, align: 'right' },
        { title: '期末应收款', width: 130, align: 'right' },
      ],
      rows: tableData.value.map((item) => [
        item.rowNo,
        item.customerName,
        `￥${formatPrice(Number(item.openingBalance || 0))}`,
        `￥${formatPrice(Number(item.receivableAmount || 0))}`,
        `￥${formatPrice(Number(item.receivedAmount || 0))}`,
        item.roundingAmount || '',
        `￥${formatPrice(Number(item.endingBalance || 0))}`,
      ]),
      amountColumnIndexes: [2, 3, 4, 5, 6],
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
    if (!printWindow) return;
    const headers = [
      '行号',
      '客户名称',
      '期初应收款',
      '增加应收款',
      '收回应收款',
      '抹零',
      '期末应收款',
    ];
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.customerName)}</td>
            <td class="amount">${formatPrintAmount(item.openingBalance)}</td>
            <td class="amount">${formatPrintAmount(item.receivableAmount)}</td>
            <td class="amount">${formatPrintAmount(item.receivedAmount)}</td>
            <td class="amount"></td>
            <td class="amount">${formatPrintAmount(item.endingBalance)}</td>
          </tr>`
      )
      .join('');

    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>应收欠款报告</title>
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
          <h1>应收欠款报告</h1>
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
    search();
  });
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .general-card {
    margin-bottom: 16px;
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
