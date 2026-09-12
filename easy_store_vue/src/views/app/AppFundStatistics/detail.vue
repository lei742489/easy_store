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

    <a-card class="general-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1180, y: getAdaptiveTableScrollY(540) }"
        :row-class="rowClass"
      />
      <div v-if="searched" class="pagination-wrap">
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
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import {
    computed,
    h,
    onMounted,
    reactive,
    ref,
  } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { useRoute } from 'vue-router';
  import { useUserStore } from '@/store';
  import { formatPrice } from '@/api/common';
  import { exportStyledXls } from '@/utils/styled-xls-export';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    FundStatisticsDetail,
    FundStatisticsDetailResult,
    FundStatisticsItem,
    listFundStatisticsDetail,
    listFundStatisticsItems,
  } from './api';

  interface DetailRow extends FundStatisticsDetail {
    itemName?: string;
    amount?: number;
  }

  const route = useRoute();
  const userStore = useUserStore();
  const loading = ref(false);
  const itemLoading = ref(false);
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const itemOptions = ref<Array<{ label: string; value: string }>>([]);
  const records = ref<DetailRow[]>([]);
  const searched = ref(false);
  const result = reactive<FundStatisticsDetailResult>({});
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });
  const form = reactive({
    itemKey: String(route.query.itemKey || '') || undefined,
    startDate:
      String(route.query.startDate || '') ||
      dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate:
      String(route.query.endDate || '') ||
      dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const hasRouteDate =
    typeof route.query.startDate === 'string' &&
    typeof route.query.endDate === 'string' &&
    Boolean(route.query.startDate) &&
    Boolean(route.query.endDate);

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
    const total =
      result.netTotal ??
      records.value.reduce((sum, record) => sum + Number(record.amount || 0), 0);
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
      const { data } = await listFundStatisticsItems({
        userId: userStore.id,
      });
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
        userId: userStore.id,
        itemKey: form.itemKey,
        startDate: form.startDate,
        endDate: form.endDate,
        current: pagination.current,
        pageSize: pagination.pageSize,
      });
      const itemName =
        itemOptions.value.find((item) => item.value === form.itemKey)?.label ||
        '';
      Object.assign(result, data || {});
      records.value = (data?.records || []).map((item, index) => ({
        ...item,
        rowNo: item.rowNo ?? (pagination.current - 1) * pagination.pageSize + index + 1,
        itemName,
        amount: Number(item.income || 0) - Number(item.expense || 0),
        businessDate:
          item.businessTime == null
            ? ''
            : dayjs(Number(item.businessTime)).format('YYYY-MM-DD'),
      }));
      pagination.current = Number(data?.current || pagination.current);
      pagination.pageSize = Number(data?.pageSize || pagination.pageSize);
      pagination.total = Number(data?.total || 0);
      searched.value = true;
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    pagination.current = 1;
    fetchData();
  };

  const reset = () => {
    form.itemKey = undefined;
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    records.value = [];
    Object.assign(result, {
      current: 1,
      pageSize: pagination.pageSize,
      total: 0,
      incomeTotal: 0,
      expenseTotal: 0,
      netTotal: 0,
      records: [],
    });
    pagination.current = 1;
    pagination.total = 0;
    searched.value = false;
    timeSelectRef.value?.setPreset(2);
  };

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const onPageChange = (current: number) => {
    pagination.current = current;
    fetchData();
  };

  const onPageSizeChange = (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.current = 1;
    fetchData();
  };

  const exportCsv = () => {
    exportStyledXls({
      fileName: '资金统计明细',
      title: '资金统计明细',
      columns: [
        { title: '行号', width: 70 },
        { title: '业务日期', width: 120 },
        { title: '业务编号', width: 180 },
        { title: '摘要', width: 180, align: 'left' },
        { title: '往来单位', width: 170, align: 'left' },
        { title: '收支项目', width: 160, align: 'left' },
        { title: '金额', width: 120, align: 'right' },
      ],
      rows: tableData.value.map((item) => [
        item.rowNo,
        item.businessDate,
        item.orderNo,
        item.summary,
        item.counterparty,
        item.itemName,
        formatAmount(item.amount),
      ]),
      amountColumnIndexes: [6],
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
    if (hasRouteDate) {
      timeSelectRef.value?.setRange([form.startDate, form.endDate]);
    } else {
      timeSelectRef.value?.setPreset(2);
    }
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

  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
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
