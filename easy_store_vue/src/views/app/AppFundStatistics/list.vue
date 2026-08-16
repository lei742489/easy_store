<template>
  <div class="container">
    <a-card class="general-card" title="资金统计报告" style="margin-top: 12px">
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
              <a-col :span="5">
                <a-form-item field="itemType" label="项目类别">
                  <a-select
                    v-model="form.itemType"
                    :options="itemTypeOptions"
                    placeholder="全部"
                    allow-clear
                  />
                </a-form-item>
              </a-col>
              <a-col :span="7">
                <a-form-item field="itemKey" label="收支项目">
                  <a-select
                    v-model="form.itemKey"
                    :options="itemOptions"
                    placeholder="全部项目"
                    :loading="itemLoading"
                    allow-clear
                    allow-search
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
            <a-button :disabled="!records.length" @click="exportCsv">
              <template #icon><icon-download /></template>
              导出
            </a-button>
            <a-button :disabled="!records.length" @click="print(true)">
              <template #icon><icon-printer /></template>
              打印
            </a-button>
            <a-button :disabled="!records.length" @click="print(false)">
              预览
            </a-button>
          </a-space>
        </a-col>
      </a-row>
    </a-card>

    <a-card class="general-card" :bordered="false">
      <a-table
        row-key="itemKey"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 900, y: 560 }"
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
    </a-card>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { useRouter } from 'vue-router';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    FundStatisticsItem,
    FundStatisticsRecord,
    FundStatisticsResult,
    listFundStatisticsItems,
    listFundStatistics,
  } from './api';

  const router = useRouter();
  const loading = ref(false);
  const records = ref<FundStatisticsRecord[]>([]);
  const result = reactive<FundStatisticsResult>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const itemLoading = ref(false);
  const itemOptions = ref<Array<{ label: string; value: string }>>([]);
  const form = reactive({
    itemType: 'all' as 'all' | 'income' | 'expense',
    itemKey: undefined as string | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const itemTypeOptions = [
    { label: '全部', value: 'all' },
    { label: '收入', value: 'income' },
    { label: '支出', value: 'expense' },
  ];

  const formatAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;
  const moneyCell = (field: keyof FundStatisticsRecord) => (record: any) => {
    const row = record.record as FundStatisticsRecord;
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
      formatAmount(value)
    );
  };
  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 80, align: 'center' },
    {
      title: '收支项目',
      dataIndex: 'itemName',
      minWidth: 300,
      align: 'left',
    },
    {
      title: '收入',
      dataIndex: 'income',
      width: 180,
      align: 'right',
      render: moneyCell('income'),
    },
    {
      title: '支出',
      dataIndex: 'expense',
      width: 180,
      align: 'right',
      render: moneyCell('expense'),
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 90,
      align: 'center',
    },
  ];
  const tableData = computed<FundStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        itemName: '',
        income: result.incomeTotal || 0,
        expense: result.expenseTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });
  const rowClass = (record: FundStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';

  const fetchItems = async () => {
    itemLoading.value = true;
    try {
      const { data } = await listFundStatisticsItems({
        itemType: form.itemType === 'all' ? undefined : form.itemType,
      });
      const options: Array<{ label: string; value: string }> = [];
      (data || []).forEach((item: FundStatisticsItem) => {
        if (item.itemKey && item.name) {
          options.push({ label: item.name, value: item.itemKey });
        }
      });
      itemOptions.value = options;
    } finally {
      itemLoading.value = false;
    }
  };
  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listFundStatistics({
        ...form,
        itemType: form.itemType === 'all' ? undefined : form.itemType,
      });
      Object.assign(result, data || {});
      records.value = data?.records || [];
    } finally {
      loading.value = false;
    }
  };
  const search = () => {
    fetchData();
  };
  const reset = () => {
    form.itemType = 'all';
    form.itemKey = undefined;
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    timeSelectRef.value?.setPreset(2);
    fetchItems();
    search();
  };
  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };
  const showDetail = async (record: FundStatisticsRecord) => {
    if (!record.itemKey) return;
    router.push({
      name: 'FundStatisticsDetail',
      query: { itemKey: record.itemKey },
    });
  };

  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;
  const exportCsv = () => {
    const headers = ['行号', '收支项目', '收入', '支出'];
    const rows = tableData.value.map((item) => [
      item.rowNo,
      item.itemName,
      item.income,
      item.expense,
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
    link.download = '资金统计.csv';
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
            <td>${escapeHtml(item.itemName)}</td>
            <td class="amount">${escapeHtml(formatAmount(item.income))}</td>
            <td class="amount">${escapeHtml(formatAmount(item.expense))}</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>资金统计报告</title>
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
          <h1>资金统计报告</h1>
          <table>
            <thead><tr><th>行号</th><th>收支项目</th><th>收入</th><th>支出</th></tr></thead>
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

  watch(
    () => form.itemType,
    () => {
      form.itemKey = undefined;
      fetchItems();
    }
  );

  onMounted(async () => {
    await fetchItems();
    fetchData();
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
