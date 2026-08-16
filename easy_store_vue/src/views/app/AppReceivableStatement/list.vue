<template>
  <div class="container">
    <a-card class="general-card" title="应收对账单" style="margin-top: 12px">
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
        <span>应收对账单</span>
        <template v-if="result.customerName">
          <span class="split">&gt;</span>
          <span>客户 “{{ result.customerName }}” 的对账单</span>
        </template>
      </div>
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1420, y: 560 }"
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
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import CustomerSelect from '@/views/app/customer/components/customer-select-modal.vue';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    ReceivableStatementRecord,
    ReceivableStatementResult,
    listReceivableStatement,
  } from './api';

  const route = useRoute();
  const loading = ref(false);
  const records = ref<ReceivableStatementRecord[]>([]);
  const result = reactive<ReceivableStatementResult>({});
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

  const formatAmount = (value?: number) => getPriceStrByH(Number(value || 0));
  const formatPlainAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;

  const moneyCell =
    (field: keyof ReceivableStatementRecord, emptyForItem = false) =>
    (record: any) => {
      const row = record.record as ReceivableStatementRecord;
      if (emptyForItem && row.rowType === 'item') return '';
      return h(
        'span',
        { class: row.isSummary ? 'summary-amount' : undefined },
        formatAmount(Number(row[field] || 0))
      );
    };

  const rowCellStyle = (record: ReceivableStatementRecord) => {
    if (record.isSummary) {
      return {
        fontWeight: 600,
        background: 'var(--color-fill-2)',
      };
    }
    if (record.rowType === 'sale') {
      return {
        background: '#e8f7ff',
      };
    }
    if (record.rowType === 'opening' || record.rowType === 'receipt') {
      return {
        background: '#ffffdd',
      };
    }
    return {};
  };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 70, align: 'center' },
    {
      title: '品名规格',
      dataIndex: 'goodsName',
      width: 220,
      align: 'left',
    },
    { title: '单位', dataIndex: 'unit', width: 80, align: 'center' },
    { title: '数量', dataIndex: 'quantity', width: 90, align: 'right' },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 100,
      align: 'right',
      render: (record) => {
        const row = record.record as ReceivableStatementRecord;
        return row.unitPrice == null ? '' : formatPrice(row.unitPrice);
      },
    },
    {
      title: '运费',
      dataIndex: 'freightAmount',
      width: 100,
      align: 'right',
      render: moneyCell('freightAmount', true),
    },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 110,
      align: 'right',
      render: moneyCell('totalAmount'),
    },
    {
      title: '折扣优惠',
      dataIndex: 'discountAmount',
      width: 110,
      align: 'right',
      render: moneyCell('discountAmount', true),
    },
    { title: '备注', dataIndex: 'note', width: 140, align: 'center' },
    {
      title: '增加应收款',
      dataIndex: 'receivableAmount',
      width: 130,
      align: 'right',
      render: moneyCell('receivableAmount', true),
    },
    {
      title: '收回应收款',
      dataIndex: 'receivedAmount',
      width: 130,
      align: 'right',
      render: moneyCell('receivedAmount', true),
    },
    { title: '抹零', dataIndex: 'roundingAmount', width: 100, align: 'right' },
    {
      title: '期末应收款',
      dataIndex: 'endingBalance',
      width: 140,
      align: 'right',
      render: moneyCell('endingBalance', true),
    },
  ].map((column) => ({
    ...column,
    bodyCellStyle: rowCellStyle,
  }));

  const tableData = computed<ReceivableStatementRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        receivableAmount: result.receivableTotal || 0,
        receivedAmount: result.receivedTotal || 0,
        endingBalance: result.endingBalance || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: ReceivableStatementRecord) => {
    if (record.isSummary) return 'summary-row';
    if (record.rowType === 'item') return 'item-row';
    if (record.rowType === 'sale') return 'sale-row';
    return 'document-row';
  };

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

  const search = async () => {
    if (!form.customerId) {
      resetResult();
      Message.warning('请选择要查询的客户');
      return;
    }
    loading.value = true;
    try {
      const { data } = await listReceivableStatement({ ...form });
      Object.assign(result, data || {});
      records.value = data?.records || [];
    } finally {
      loading.value = false;
    }
  };

  const getCsvValue = (
    item: ReceivableStatementRecord,
    field: keyof ReceivableStatementRecord
  ) => item[field] ?? '';

  const exportCsv = () => {
    if (!tableData.value.length) return;
    const headers = columns.map((item) => item.title).join(',');
    const fields: Array<keyof ReceivableStatementRecord> = [
      'rowNo',
      'goodsName',
      'unit',
      'quantity',
      'unitPrice',
      'freightAmount',
      'totalAmount',
      'discountAmount',
      'note',
      'receivableAmount',
      'receivedAmount',
      'roundingAmount',
      'endingBalance',
    ];
    const rows = tableData.value.map((item) =>
      fields
        .map(
          (field) => `"${String(getCsvValue(item, field)).replace(/"/g, '""')}"`
        )
        .join(',')
    );
    const blob = new Blob([`\uFEFF${[headers, ...rows].join('\n')}`], {
      type: 'text/csv;charset=utf-8;',
    });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `应收对账单_${result.customerName || ''}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const escapeHtml = (value: unknown) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');

  const printableValue = (
    item: ReceivableStatementRecord,
    field: keyof ReceivableStatementRecord
  ) => {
    if (
      [
        'unitPrice',
        'freightAmount',
        'totalAmount',
        'discountAmount',
        'receivableAmount',
        'receivedAmount',
        'endingBalance',
      ].includes(field)
    ) {
      const value = item[field] as number | undefined;
      return value == null ? '' : formatPlainAmount(value);
    }
    return escapeHtml(item[field]);
  };

  const print = () => {
    if (!tableData.value.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      Message.warning('浏览器阻止了打印窗口，请允许弹窗后重试');
      return;
    }

    const headers = [
      '行号',
      '品名规格',
      '单位',
      '数量',
      '单价',
      '运费',
      '金额',
      '折扣优惠',
      '备注',
      '增加应收款',
      '收回应收款',
      '抹零',
      '期末应收款',
    ];
    const fields: Array<keyof ReceivableStatementRecord> = [
      'rowNo',
      'goodsName',
      'unit',
      'quantity',
      'unitPrice',
      'freightAmount',
      'totalAmount',
      'discountAmount',
      'note',
      'receivableAmount',
      'receivedAmount',
      'roundingAmount',
      'endingBalance',
    ];
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            ${fields
              .map((field) => {
                const isAmount = [
                  'unitPrice',
                  'freightAmount',
                  'totalAmount',
                  'discountAmount',
                  'receivableAmount',
                  'receivedAmount',
                  'endingBalance',
                ].includes(field);
                return `<td class="${
                  isAmount ? 'amount' : ''
                }">${printableValue(item, field)}</td>`;
              })
              .join('')}
          </tr>`
      )
      .join('');

    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>应收对账单</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 24px; color: #1d2129; font-family: Arial, "Microsoft YaHei", sans-serif; }
            h1 { margin: 0 0 18px; text-align: center; font-size: 20px; }
            table { width: 100%; border-collapse: collapse; font-size: 12px; }
            th, td { padding: 6px; border: 1px solid #c9cdd4; text-align: center; }
            th { background: #f2f3f5; font-weight: 600; }
            td.amount { text-align: right; white-space: nowrap; }
          </style>
        </head>
        <body>
          <h1>应收对账单${
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
    const { customerId } = route.query;
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

  :deep(.document-row) {
    background: #ffffdd;
  }

  :deep(.sale-row) {
    background: #e8f7ff;
  }

  :deep(.summary-amount) {
    color: rgb(var(--arcoblue-6));
  }
</style>
