<template>
  <div class="container">
    <a-card class="general-card" title="营业员统计" style="margin-top: 12px">
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
            <a-row>
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
              <template #icon><icon-search /></template>
              统计
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
    </a-card>

    <a-card class="general-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1200, y: getAdaptiveTableScrollY(560) }"
        :row-class="rowClass"
      >
        <template #operations="{ record }">
          <a-button
            v-if="!record.isSummary"
            type="text"
            size="small"
            @click="showDetail(record)"
          >
            明细
          </a-button>
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
      v-model:visible="detailVisible"
      width="88vw"
      :footer="false"
      :body-style="{ minHeight: '560px' }"
      :mask-closable="false"
    >
      <template #title>营业员销售明细</template>
      <a-row class="detail-filter">
        <a-col :flex="1">
          <a-form
            :model="detailForm"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="right"
            auto-label-width
          >
            <a-row :gutter="24">
              <a-col :span="13">
                <a-form-item field="businessDate" label="日期">
                  <time-select
                    ref="detailTimeSelectRef"
                    :default-time-idx="2"
                    @change="detailTimeSelectChange"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="7">
                <a-form-item field="groupBy" label="统计方式">
                  <a-radio-group v-model="detailForm.groupBy" type="button">
                    <a-radio value="customer">按客户</a-radio>
                    <a-radio value="goods">按商品</a-radio>
                  </a-radio-group>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-col>
        <a-col :flex="'86px'" style="text-align: right">
          <a-space direction="vertical" :size="12">
            <a-button
              type="primary"
              :loading="detailLoading"
              @click="fetchDetail"
            >
              <template #icon><icon-search /></template>
              统计
            </a-button>
            <a-button @click="resetDetail">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      <a-divider style="margin-top: 4px" />
      <div class="detail-toolbar">
        <a-space>
          <a-button
            type="primary"
            :disabled="!detailRecords.length"
            @click="exportDetailCsv"
          >
            <template #icon><icon-download /></template>
            导出
          </a-button>
          <a-button
            :disabled="!detailRecords.length"
            @click="printDetail(true)"
          >
            <template #icon><icon-printer /></template>
            打印
          </a-button>
          <a-button
            :disabled="!detailRecords.length"
            @click="printDetail(false)"
          >
            预览
          </a-button>
        </a-space>
      </div>
      <a-table
        row-key="rowNo"
        :loading="detailLoading"
        :pagination="false"
        :columns="detailColumns"
        :data="detailTableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1100, y: getAdaptiveTableScrollY(520) }"
        :row-class="detailRowClass"
      />
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, h, onMounted, reactive, ref, watch } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    CashierStatisticsDetail,
    CashierStatisticsRecord,
    CashierStatisticsResult,
    listCashierStatistics,
    listCashierStatisticsDetail,
  } from './api';

  const loading = ref(false);
  const records = ref<CashierStatisticsRecord[]>([]);
  const result = reactive<CashierStatisticsResult>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const detailTimeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailCashierId = ref<number | string>();
  const detailRecords = ref<CashierStatisticsDetail[]>([]);
  const form = reactive({
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const detailForm = reactive({
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
    groupBy: 'customer' as 'customer' | 'goods',
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });

  const amountClass = (value: number, isSummary?: boolean) => {
    if (isSummary) return 'summary-amount';
    return value < 0 ? 'danger-amount' : undefined;
  };
  const moneyCell = (field: keyof CashierStatisticsRecord) => (record: any) => {
    const row = record.record as CashierStatisticsRecord;
    const value = Number(row[field] || 0);
    return h(
      'span',
      { class: amountClass(value, row.isSummary) },
      `¥${formatPrice(value)}`
    );
  };
  const rateCell =
    (field: 'commissionRate' | 'profitRate') => (record: any) => {
      const row = record.record as CashierStatisticsRecord;
      return h(
        'span',
        { class: row.isSummary ? 'summary-amount' : undefined },
        `${Number(row[field] || 0).toFixed(2)}%`
      );
    };
  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    { title: '营业员', dataIndex: 'cashierName', minWidth: 180, align: 'left' },
    {
      title: '销售数量',
      dataIndex: 'quantity',
      width: 120,
      align: 'right',
      render: (record: any) => Number(record.record.quantity || 0).toFixed(2),
    },
    {
      title: '销售金额',
      dataIndex: 'salesAmount',
      width: 150,
      align: 'right',
      render: moneyCell('salesAmount'),
    },
    {
      title: '利润金额',
      dataIndex: 'profitAmount',
      width: 150,
      align: 'right',
      render: moneyCell('profitAmount'),
    },
    {
      title: '提成比例',
      dataIndex: 'commissionRate',
      width: 120,
      align: 'right',
      render: rateCell('commissionRate'),
    },
    {
      title: '提成金额',
      dataIndex: 'commissionAmount',
      width: 150,
      align: 'right',
      render: moneyCell('commissionAmount'),
    },
    {
      title: '利润率',
      dataIndex: 'profitRate',
      width: 120,
      align: 'right',
      render: rateCell('profitRate'),
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 90,
      align: 'center',
    },
  ];

  const tableData = computed<CashierStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        cashierName: '',
        quantity: result.quantityTotal || 0,
        salesAmount: result.salesTotal || 0,
        profitAmount: result.profitTotal || 0,
        commissionAmount: result.commissionTotal || 0,
        profitRate: result.profitRateTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });
  const rowClass = (record: CashierStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';

  const detailGroupTitle = computed(() =>
    detailForm.groupBy === 'goods' ? '品名规格' : '客户名称'
  );
  const detailMoneyCell =
    (field: keyof CashierStatisticsDetail) => (record: any) => {
      const row = record.record as CashierStatisticsDetail;
      const value = Number(row[field] || 0);
      return h(
        'span',
        { class: amountClass(value, row.isSummary) },
        `¥${formatPrice(value)}`
      );
    };
  const detailColumns = computed<TableColumnData[]>(() => [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: detailGroupTitle.value,
      dataIndex: 'groupName',
      minWidth: 240,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    { title: '单位', dataIndex: 'unit', width: 90, align: 'center' },
    {
      title: '销售数量',
      dataIndex: 'quantity',
      width: 120,
      align: 'right',
      render: (record: any) => Number(record.record.quantity || 0).toFixed(2),
    },
    {
      title: '销售金额',
      dataIndex: 'salesAmount',
      width: 150,
      align: 'right',
      render: detailMoneyCell('salesAmount'),
    },
    {
      title: '利润金额',
      dataIndex: 'profitAmount',
      width: 150,
      align: 'right',
      render: detailMoneyCell('profitAmount'),
    },
    {
      title: '提成金额',
      dataIndex: 'commissionAmount',
      width: 150,
      align: 'right',
      render: detailMoneyCell('commissionAmount'),
    },
    {
      title: '利润率',
      dataIndex: 'profitRate',
      width: 120,
      align: 'right',
      render: (record: any) =>
        `${Number(record.record.profitRate || 0).toFixed(2)}%`,
    },
  ]);
  const detailTableData = computed<CashierStatisticsDetail[]>(() => {
    if (!detailRecords.value.length) return [];
    const totals = detailRecords.value.reduce(
      (summary, record) => ({
        quantity: summary.quantity + Number(record.quantity || 0),
        salesAmount: summary.salesAmount + Number(record.salesAmount || 0),
        profitAmount: summary.profitAmount + Number(record.profitAmount || 0),
        commissionAmount:
          summary.commissionAmount + Number(record.commissionAmount || 0),
      }),
      {
        quantity: 0,
        salesAmount: 0,
        profitAmount: 0,
        commissionAmount: 0,
      }
    );
    return [
      {
        rowNo: '合计',
        groupName: '',
        unit: '',
        quantity: totals.quantity,
        salesAmount: totals.salesAmount,
        profitAmount: totals.profitAmount,
        commissionAmount: totals.commissionAmount,
        profitRate:
          totals.salesAmount === 0
            ? 0
            : (totals.profitAmount * 100) / totals.salesAmount,
        isSummary: true,
      },
      ...detailRecords.value,
    ];
  });
  const detailRowClass = (record: CashierStatisticsDetail) =>
    record.isSummary ? 'summary-row' : '';

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listCashierStatistics({
        ...form,
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
  const search = () => {
    pagination.current = 1;
    fetchData();
  };
  const reset = () => {
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    timeSelectRef.value?.setPreset(2);
    search();
  };
  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
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

  const fetchDetail = async () => {
    if (detailCashierId.value === undefined || detailCashierId.value === null) {
      return;
    }
    detailLoading.value = true;
    try {
      const { data } = await listCashierStatisticsDetail({
        cashierId: detailCashierId.value,
        ...detailForm,
      });
      detailRecords.value = data || [];
    } finally {
      detailLoading.value = false;
    }
  };
  const showDetail = (record: CashierStatisticsRecord) => {
    if (record.cashierId === undefined || record.cashierId === null) return;
    detailCashierId.value = record.cashierId;
    detailForm.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    detailForm.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    detailForm.groupBy = 'customer';
    detailTimeSelectRef.value?.setPreset(2);
    detailRecords.value = [];
    detailVisible.value = true;
    fetchDetail();
  };
  const resetDetail = () => {
    detailForm.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    detailForm.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    detailForm.groupBy = 'customer';
    detailTimeSelectRef.value?.setPreset(2);
    fetchDetail();
  };
  const detailTimeSelectChange = (dates: string[]) => {
    [detailForm.startDate, detailForm.endDate] = dates;
  };
  watch(
    () => detailForm.groupBy,
    () => {
      if (detailVisible.value) {
        fetchDetail();
      }
    }
  );

  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;
  const exportCsv = () => {
    const headers = [
      '行号',
      '营业员',
      '销售数量',
      '销售金额',
      '利润金额',
      '提成比例',
      '提成金额',
      '利润率',
    ];
    const rows = tableData.value.map((item) => [
      item.rowNo,
      item.cashierName,
      item.quantity,
      item.salesAmount,
      item.profitAmount,
      item.commissionRate == null
        ? ''
        : `${Number(item.commissionRate).toFixed(2)}%`,
      item.commissionAmount,
      `${Number(item.profitRate || 0).toFixed(2)}%`,
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
    link.download = '营业员统计.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const exportDetailCsv = () => {
    const headers = [
      '行号',
      detailGroupTitle.value,
      '单位',
      '销售数量',
      '销售金额',
      '利润金额',
      '提成金额',
      '利润率',
    ];
    const rows = detailTableData.value.map((item) => [
      item.rowNo,
      item.groupName,
      item.unit,
      item.quantity,
      item.salesAmount,
      item.profitAmount,
      item.commissionAmount,
      `${Number(item.profitRate || 0).toFixed(2)}%`,
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
    link.download = '营业员销售明细.csv';
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
    if (!printWindow) return;
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.cashierName)}</td>
            <td class="amount">${Number(item.quantity || 0).toFixed(2)}</td>
            <td class="amount">¥${formatPrice(
              Number(item.salesAmount || 0)
            )}</td>
            <td class="amount">¥${formatPrice(
              Number(item.profitAmount || 0)
            )}</td>
            <td class="amount">${
              item.commissionRate == null
                ? ''
                : `${Number(item.commissionRate).toFixed(2)}%`
            }</td>
            <td class="amount">¥${formatPrice(
              Number(item.commissionAmount || 0)
            )}</td>
            <td class="amount">${Number(item.profitRate || 0).toFixed(2)}%</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>营业员统计</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 24px; color: #1d2129; font-family: Arial, "Microsoft YaHei", sans-serif; }
            h1 { margin: 0 0 18px; text-align: center; font-size: 20px; }
            table { width: 100%; border-collapse: collapse; font-size: 12px; }
            th, td { padding: 7px; border: 1px solid #c9cdd4; text-align: center; }
            th { background: #f2f3f5; font-weight: 600; }
            td.amount { text-align: right; }
          </style>
        </head>
        <body>
          <h1>营业员统计</h1>
          <table>
            <thead><tr><th>行号</th><th>营业员</th><th>销售数量</th><th>销售金额</th><th>利润金额</th><th>提成比例</th><th>提成金额</th><th>利润率</th></tr></thead>
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

  const printDetail = (autoPrint: boolean) => {
    if (!detailTableData.value.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    const rows = detailTableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.groupName)}</td>
            <td>${escapeHtml(item.unit)}</td>
            <td class="amount">${Number(item.quantity || 0).toFixed(2)}</td>
            <td class="amount">${escapeHtml(formatPrice(item.salesAmount))}</td>
            <td class="amount">${escapeHtml(
              formatPrice(item.profitAmount)
            )}</td>
            <td class="amount">${escapeHtml(
              formatPrice(item.commissionAmount)
            )}</td>
            <td class="amount">${Number(item.profitRate || 0).toFixed(2)}%</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>营业员销售明细</title>
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
          <h1>营业员销售明细</h1>
          <table>
            <thead>
              <tr>
                <th>行号</th><th>${escapeHtml(
                  detailGroupTitle.value
                )}</th><th>单位</th>
                <th>销售数量</th><th>销售金额</th><th>利润金额</th>
                <th>提成金额</th><th>利润率</th>
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

  onMounted(fetchData);
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .general-card {
    margin-bottom: 16px;
  }

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
  }

  .detail-filter {
    min-height: 68px;
  }

  .detail-toolbar {
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--color-border-2);
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
