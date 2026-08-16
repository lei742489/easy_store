<template>
  <div class="container">
    <a-card class="general-card" title="利润统计" style="margin-top: 12px">
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
                <a-form-item field="customerId" label="往来单位">
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

    <a-card class="general-card report-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 980, y: 560 }"
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
      width="90vw"
      :footer="false"
      :body-style="{ minHeight: '560px' }"
      :mask-closable="false"
    >
      <template #title>利润明细</template>
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
            <a-row>
              <a-col :span="14">
                <a-form-item field="businessDate" label="日期">
                  <time-select
                    ref="detailTimeSelectRef"
                    :default-time-idx="2"
                    @change="detailTimeSelectChange"
                  />
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
              @click="searchDetail"
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
      <a-table
        row-key="rowNo"
        :loading="detailLoading"
        :pagination="false"
        :columns="detailColumns"
        :data="detailTableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1240, y: 520 }"
        :row-class="rowClass"
      />
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import CustomerSelect from '@/views/app/customer/components/customer-select-modal.vue';
  import {
    listProfitStatistics,
    listProfitStatisticsDetail,
    ProfitStatisticsDetail,
    ProfitStatisticsRecord,
    ProfitStatisticsResult,
  } from './api';

  const loading = ref(false);
  const records = ref<ProfitStatisticsRecord[]>([]);
  const result = reactive<ProfitStatisticsResult>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const detailTimeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailRecords = ref<ProfitStatisticsDetail[]>([]);
  const detailCustomerId = ref<number | string>();
  const form = reactive({
    customerId: undefined as number | string | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const detailForm = reactive({
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });

  const amountCell = (field: keyof ProfitStatisticsRecord) => (record: any) => {
    const row = record.record as ProfitStatisticsRecord;
    const value = Number(row[field] || 0);
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : undefined },
      `¥${formatPrice(value)}`
    );
  };
  const profitRateCell = (record: any) => {
    const row = record.record as ProfitStatisticsRecord;
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : undefined },
      `${Number(row.profitRate || 0).toFixed(2)}%`
    );
  };
  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '往来单位',
      dataIndex: 'customerName',
      minWidth: 240,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '折后金额',
      dataIndex: 'discountedAmount',
      width: 160,
      align: 'right',
      render: amountCell('discountedAmount'),
    },
    {
      title: '成本金额',
      dataIndex: 'costAmount',
      width: 160,
      align: 'right',
      render: amountCell('costAmount'),
    },
    {
      title: '利润金额',
      dataIndex: 'profitAmount',
      width: 160,
      align: 'right',
      render: amountCell('profitAmount'),
    },
    {
      title: '利润率',
      dataIndex: 'profitRate',
      width: 130,
      align: 'right',
      render: profitRateCell,
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 90,
      align: 'center',
    },
  ];
  const detailAmountCell =
    (field: keyof ProfitStatisticsDetail) => (record: any) => {
      const row = record.record as ProfitStatisticsDetail;
      const value = Number(row[field] || 0);
      let className: string | undefined;
      if (row.isSummary) {
        className = 'summary-amount';
      } else if (value < 0) {
        className = 'danger-amount';
      }
      return h(
        'span',
        {
          class: className,
        },
        `¥${formatPrice(value)}`
      );
    };
  const detailProfitRateCell = (record: any) => {
    const row = record.record as ProfitStatisticsDetail;
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : undefined },
      `${Number(row.profitRate || 0).toFixed(2)}%`
    );
  };
  const detailColumns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '日期',
      dataIndex: 'businessDate',
      width: 120,
      align: 'center',
    },
    {
      title: '品名规格',
      dataIndex: 'goodsName',
      minWidth: 220,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    { title: '单位', dataIndex: 'unit', width: 80, align: 'center' },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 120,
      align: 'right',
      render: (record: any) => Number(record.record.quantity || 0).toFixed(2),
    },
    {
      title: '折后单价',
      dataIndex: 'discountedUnitPrice',
      width: 145,
      align: 'right',
      render: detailAmountCell('discountedUnitPrice'),
    },
    {
      title: '折后金额',
      dataIndex: 'discountedAmount',
      width: 160,
      align: 'right',
      render: detailAmountCell('discountedAmount'),
    },
    {
      title: '成本金额',
      dataIndex: 'costAmount',
      width: 160,
      align: 'right',
      render: detailAmountCell('costAmount'),
    },
    {
      title: '利润金额',
      dataIndex: 'profitAmount',
      width: 160,
      align: 'right',
      render: detailAmountCell('profitAmount'),
    },
    {
      title: '利润率',
      dataIndex: 'profitRate',
      width: 120,
      align: 'right',
      render: detailProfitRateCell,
    },
  ];

  const tableData = computed<ProfitStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        customerName: '',
        discountedAmount: result.discountedTotal || 0,
        costAmount: result.costTotal || 0,
        profitAmount: result.profitTotal || 0,
        profitRate: result.profitRateTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });
  const rowClass = (record: ProfitStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';
  const detailTableData = computed<ProfitStatisticsDetail[]>(() => {
    if (!detailRecords.value.length) return [];
    const totals = detailRecords.value.reduce(
      (summary, record) => ({
        quantity: summary.quantity + Number(record.quantity || 0),
        discountedAmount:
          summary.discountedAmount + Number(record.discountedAmount || 0),
        costAmount: summary.costAmount + Number(record.costAmount || 0),
        profitAmount: summary.profitAmount + Number(record.profitAmount || 0),
      }),
      {
        quantity: 0,
        discountedAmount: 0,
        costAmount: 0,
        profitAmount: 0,
      }
    );
    return [
      {
        rowNo: '合计',
        businessDate: '',
        goodsName: '',
        unit: '',
        quantity: totals.quantity,
        discountedUnitPrice: undefined,
        discountedAmount: totals.discountedAmount,
        costAmount: totals.costAmount,
        profitAmount: totals.profitAmount,
        profitRate:
          totals.discountedAmount === 0
            ? 0
            : (totals.profitAmount * 100) / totals.discountedAmount,
        isSummary: true,
      },
      ...detailRecords.value,
    ];
  });

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listProfitStatistics({
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
    form.customerId = undefined;
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
    if (
      detailCustomerId.value === undefined ||
      detailCustomerId.value === null
    ) {
      return;
    }
    detailLoading.value = true;
    try {
      const { data } = await listProfitStatisticsDetail({
        customerId: detailCustomerId.value,
        ...detailForm,
      });
      detailRecords.value = data || [];
    } finally {
      detailLoading.value = false;
    }
  };
  const showDetail = (record: ProfitStatisticsRecord) => {
    if (record.customerId === undefined || record.customerId === null) {
      Message.warning('往来单位数据不完整');
      return;
    }
    detailCustomerId.value = record.customerId;
    detailForm.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    detailForm.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    detailTimeSelectRef.value?.setPreset(2);
    detailRecords.value = [];
    detailVisible.value = true;
    fetchDetail();
  };
  const searchDetail = () => {
    fetchDetail();
  };
  const resetDetail = () => {
    detailForm.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    detailForm.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    detailTimeSelectRef.value?.setPreset(2);
    fetchDetail();
  };
  const detailTimeSelectChange = (dates: string[]) => {
    [detailForm.startDate, detailForm.endDate] = dates;
  };

  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;
  const exportCsv = () => {
    const headers = [
      '行号',
      '往来单位',
      '折后金额',
      '成本金额',
      '利润金额',
      '利润率',
    ];
    const rows = tableData.value.map((item) => [
      item.rowNo,
      item.customerName,
      item.discountedAmount,
      item.costAmount,
      item.profitAmount,
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
    link.download = '利润统计.csv';
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
            <td>${escapeHtml(item.customerName)}</td>
            <td class="amount">¥${formatPrice(
              Number(item.discountedAmount || 0)
            )}</td>
            <td class="amount">¥${formatPrice(
              Number(item.costAmount || 0)
            )}</td>
            <td class="amount">¥${formatPrice(
              Number(item.profitAmount || 0)
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
          <title>利润统计</title>
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
          <h1>利润统计</h1>
          <table>
            <thead><tr><th>行号</th><th>往来单位</th><th>折后金额</th><th>成本金额</th><th>利润金额</th><th>利润率</th></tr></thead>
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

  :deep(.summary-row) {
    font-weight: 600;
    background: var(--color-fill-2);
  }

  :deep(.summary-amount) {
    color: rgb(var(--arcoblue-6));
  }

  .detail-filter {
    min-height: 68px;
  }

  .danger-amount {
    color: rgb(var(--red-6));
  }
</style>
