<template>
  <div class="container">
    <a-card class="general-card" title="资金统计明细" :bordered="false" style="margin-top: 12px;">
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
            @submit-success="search"
          >
            <a-row :gutter="24">
              <a-col :span="9">
                <a-form-item field="itemKey" label="收支项目">
                  <a-select
                    v-model="form.itemKey"
                    :options="itemOptions"
                    :loading="itemLoading"
                    placeholder="请选择收支项目"
                    allow-search
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="15">
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
              查询
            </a-button>
            <a-button @click="reset">
              <template #icon><icon-refresh /></template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <a-divider class="toolbar-divider" />
      <a-space class="toolbar">
        <a-button
          type="primary"
          :disabled="!tableData.length"
          @click="exportCsv"
        >
          <template #icon><icon-download /></template>
          导出
        </a-button>
        <a-button :disabled="!tableData.length" @click="print(true)">
          <template #icon><icon-printer /></template>
          打印
        </a-button>
        <a-button :disabled="!tableData.length" @click="print(false)">
          预览
        </a-button>
      </a-space>
    </a-card>

    <a-card class="general-card table-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1180, y: getAdaptiveTableScrollY(500) }"
        :row-class="rowClass"
      />
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { useRoute } from 'vue-router';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    FundStatisticsDetail,
    FundStatisticsItem,
    listFundStatisticsDetail,
    listFundStatisticsItems,
  } from './api';

  interface DetailRow extends FundStatisticsDetail {
    itemName?: string;
    amount?: number;
  }

  const route = useRoute();
  const loading = ref(false);
  const itemLoading = ref(false);
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const itemOptions = ref<Array<{ label: string; value: string }>>([]);
  const records = ref<DetailRow[]>([]);
  const searched = ref(false);
  const form = reactive({
    itemKey: String(route.query.itemKey || '') || undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });

  const formatAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;

  const amountCell = (record: any) => {
    const row = record.record as DetailRow;
    const amount = Number(row.amount || 0);
    return h(
      'span',
      { class: amount < 0 ? 'negative-amount' : undefined },
      formatAmount(amount)
    );
  };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 80, align: 'center' },
    {
      title: '业务日期',
      dataIndex: 'businessDate',
      width: 135,
      align: 'left',
    },
    {
      title: '业务编号',
      dataIndex: 'orderNo',
      width: 200,
      align: 'left',
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      minWidth: 220,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '往来单位',
      dataIndex: 'counterparty',
      minWidth: 180,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '收支项目',
      dataIndex: 'itemName',
      width: 160,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 170,
      align: 'right',
      render: amountCell,
    },
  ];

  const tableData = computed<DetailRow[]>(() => {
    if (!searched.value) return [];
    const total = records.value.reduce(
      (sum, record) => sum + Number(record.amount || 0),
      0
    );
    return [
      {
        rowNo: '合计',
        amount: total,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: DetailRow) =>
    record.isSummary ? 'summary-row' : '';

  const fetchItems = async () => {
    itemLoading.value = true;
    try {
      const { data } = await listFundStatisticsItems();
      itemOptions.value = (data || [])
        .filter((item: FundStatisticsItem) => item.itemKey && item.name)
        .map((item: FundStatisticsItem) => ({
          label: item.name as string,
          value: item.itemKey as string,
        }));
    } finally {
      itemLoading.value = false;
    }
  };

  const fetchData = async () => {
    if (!form.itemKey) {
      Message.warning('请选择收支项目');
      return;
    }
    loading.value = true;
    try {
      const { data } = await listFundStatisticsDetail({
        itemKey: form.itemKey,
        startDate: form.startDate,
        endDate: form.endDate,
      });
      const itemName =
        itemOptions.value.find((item) => item.value === form.itemKey)?.label ||
        '';
      records.value = (data || []).map((item, index) => ({
        ...item,
        rowNo: index + 1,
        itemName,
        amount: Number(item.income || 0) - Number(item.expense || 0),
        businessDate:
          item.businessTime == null
            ? ''
            : dayjs(Number(item.businessTime)).format('YYYY-MM-DD'),
      }));
      searched.value = true;
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    fetchData();
  };

  const reset = () => {
    form.itemKey = undefined;
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    records.value = [];
    searched.value = false;
    timeSelectRef.value?.setPreset(2);
  };

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;

  const exportCsv = () => {
    const headers = [
      '行号',
      '业务日期',
      '业务编号',
      '摘要',
      '往来单位',
      '收支项目',
      '金额',
    ];
    const rows = tableData.value.map((item) => [
      item.rowNo,
      item.businessDate,
      item.orderNo,
      item.summary,
      item.counterparty,
      item.itemName,
      item.amount,
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
    link.download = '资金统计明细.csv';
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
            <td>${escapeHtml(item.itemName)}</td>
            <td class="amount">${escapeHtml(formatAmount(item.amount))}</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>资金统计明细</title>
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
          <h1>资金统计明细</h1>
          <table>
            <thead>
              <tr>
                <th>行号</th><th>业务日期</th><th>业务编号</th>
                <th>摘要</th><th>往来单位</th><th>收支项目</th><th>金额</th>
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

  onMounted(async () => {
    await fetchItems();
    if (form.itemKey) {
      fetchData();
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

  .toolbar {
    min-height: 32px;
  }

  .toolbar-divider {
    margin: 12px 0;
  }

  .table-card {
    min-height: calc(100vh - 190px);
  }

  :deep(.summary-row) {
    font-weight: 600;
    background: var(--color-fill-2);
  }

  :deep(.summary-row .arco-table-td) {
    color: rgb(var(--arcoblue-6));
  }

  .negative-amount {
    color: rgb(var(--red-6));
  }
</style>
