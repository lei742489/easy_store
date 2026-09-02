<template>
  <div class="container">
    <a-card class="general-card" :title="pageTitle" style="margin-top: 12px">
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
                <a-form-item field="supplierId" label="供应商">
                  <supplier-select
                    v-model:supplier-id="form.supplierId"
                    :placeholder="isStatistics ? '全部供应商' : '请选择'"
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
              {{ isStatistics ? '统计' : '查询' }}
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
      <div v-if="!isStatistics" class="report-title">
        <span>{{ pageTitle }}</span>
        <template v-if="result.supplierName">
          <span class="split">&gt;</span>
          <span>供应商 “{{ result.supplierName }}” 的{{ reportSuffix }}</span>
        </template>
      </div>
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: tableScrollX, y: getAdaptiveTableScrollY(560) }"
        :row-class="rowClass"
      >
        <template #empty>
          <a-empty :description="emptyText" />
        </template>

        <template #operations="{ record }" >
          <a-button type="text" size="small" @click="goDetail(record)" v-if="record.supplierName">
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
  import { useRoute, useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import SupplierSelect from '@/views/app/AppSupplier/components/SupplierSelectModal.vue';
  import { list as getSupplierList } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import TimeSelect from '@/components/menu/time-select.vue';
  import {
    PayableDetailRecord,
    PayableReportResult,
    PayableStatementRecord,
    PayableStatisticsRecord,
    listPayableDetail,
    listPayableStatement,
    listPayableStatistics,
  } from './api';

  type ReportMode = 'detail' | 'statement' | 'statistics';
  type PayableRecord =
    | PayableDetailRecord
    | PayableStatementRecord
    | PayableStatisticsRecord;

  const route = useRoute();
  const router = useRouter();
  const loading = ref(false);
  const records = ref<PayableRecord[]>([]);
  const result = reactive<PayableReportResult<PayableRecord>>({});
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const form = reactive<{
    supplierId?: number | string;
    startDate?: string;
    endDate?: string;
  }>({
    supplierId: undefined as number | string | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });

  const mode = computed<ReportMode>(() => {
    if (route.name === 'PayableStatement') return 'statement';
    if (route.name === 'PayableStatistics') return 'statistics';
    return 'detail';
  });
  const isStatement = computed(() => mode.value === 'statement');
  const isStatistics = computed(() => mode.value === 'statistics');
  const pageTitle = computed(() => {
    if (isStatement.value) return '应付对账单';
    if (isStatistics.value) return '应付欠款报告';
    return '应付欠款明细';
  });
  const printTitle = computed(() =>
    result.supplierName
      ? `${pageTitle.value} - ${result.supplierName}`
      : pageTitle.value
  );
  const reportSuffix = computed(() =>
    isStatement.value ? '对账单' : '明细账'
  );
  const emptyText = computed(() => {
    if (isStatistics.value) return '暂无欠款供应商';
    return '请选择供应商后点击统计';
  });
  const tableScrollX = computed(() => {
    if (isStatement.value) return 1420;
    if (isStatistics.value) return 1120;
    return 980;
  });

  const formatAmount = (value?: number) => getPriceStrByH(Number(value || 0));
  const formatPlainAmount = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;
  const amountClass = (value?: number, isSummary?: boolean) => {
    if (isSummary) return 'summary-amount';
    if (Number(value || 0) < 0) return 'danger-amount';
    return undefined;
  };

  const moneyCell =
    (field: keyof PayableRecord, emptyForItem = false) =>
    (record: any) => {
      const row = record.record as PayableRecord;
      if (emptyForItem && (row as PayableStatementRecord).rowType === 'item') {
        return '';
      }
      const value = Number((row as any)[field] || 0);
      return h(
        'span',
        { class: amountClass(value, row.isSummary) },
        formatAmount(value)
      );
    };

  const rowCellStyle = (record: PayableRecord) => {
    const row = record as PayableStatementRecord;
    if (record.isSummary) {
      return {
        fontWeight: 600,
        background: 'var(--color-fill-2)',
      };
    }
    if (row.rowType === 'purchase') {
      return {
        background: '#e8f7ff',
      };
    }
    if (row.rowType === 'opening' || row.rowType === 'payment') {
      return {
        background: '#ffffdd',
      };
    }
    return {};
  };

  const goDetail = (record: PayableStatisticsRecord) => {
    if (!record.supplierId || record.isSummary) return;
    router.push({
      path: '/custom/payableDetail',
      query: {
        supplierId: record.supplierId,
        startDate: form.startDate,
        endDate: form.endDate,
      },
    });
  };

  const detailColumns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
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
    { title: '摘要', dataIndex: 'summary', width: 220, align: 'center' },
    {
      title: '增加应付款',
      dataIndex: 'payableAmount',
      width: 150,
      align: 'right',
      render: moneyCell('payableAmount'),
    },
    {
      title: '付出应付款',
      dataIndex: 'paidAmount',
      width: 150,
      align: 'right',
      render: moneyCell('paidAmount'),
    },
    {
      title: '期末应付款',
      dataIndex: 'endingBalance',
      width: 160,
      align: 'right',
      render: moneyCell('endingBalance'),
    },
  ];

  const statisticsColumns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '供应商名称',
      dataIndex: 'supplierName',
      width: 260,
      align: 'left',
    },
    {
      title: '期初应付款',
      dataIndex: 'openingBalance',
      width: 160,
      align: 'right',
      render: moneyCell('openingBalance'),
    },
    {
      title: '增加应付款',
      dataIndex: 'payableAmount',
      width: 160,
      align: 'right',
      render: moneyCell('payableAmount'),
    },
    {
      title: '付出应付款',
      dataIndex: 'paidAmount',
      width: 160,
      align: 'right',
      render: moneyCell('paidAmount'),
    },
    { title: '抹零', dataIndex: 'roundingAmount', width: 120, align: 'right' },
    {
      title: '期末应付款',
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

  const statementColumns: TableColumnData[] = [
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
        const row = record.record as PayableStatementRecord;
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
      title: '增加应付款',
      dataIndex: 'payableAmount',
      width: 130,
      align: 'right',
      render: moneyCell('payableAmount', true),
    },
    {
      title: '付出应付款',
      dataIndex: 'paidAmount',
      width: 130,
      align: 'right',
      render: moneyCell('paidAmount', true),
    },
    { title: '抹零', dataIndex: 'roundingAmount', width: 100, align: 'right' },
    {
      title: '期末应付款',
      dataIndex: 'endingBalance',
      width: 140,
      align: 'right',
      render: moneyCell('endingBalance', true),
    },
  ].map((column) => ({
    ...column,
    bodyCellStyle: rowCellStyle,
  }));

  const columns = computed<TableColumnData[]>(() => {
    if (isStatement.value) return statementColumns;
    if (isStatistics.value) return statisticsColumns;
    return detailColumns;
  });

  const tableData = computed<PayableRecord[]>(() => {
    if (!records.value.length) return [];
    if (isStatistics.value) {
      return [
        {
          rowNo: '合计',
          supplierName: '',
          openingBalance: result.openingTotal || 0,
          payableAmount: result.payableTotal || 0,
          paidAmount: result.paidTotal || 0,
          roundingAmount: '',
          endingBalance: result.endingTotal || 0,
          isSummary: true,
        } as PayableStatisticsRecord,
        ...records.value,
      ];
    }
    if (isStatement.value) {
      return [
        {
          rowNo: '合计',
          payableAmount: result.payableTotal || 0,
          paidAmount: result.paidTotal || 0,
          endingBalance: result.endingBalance || 0,
          isSummary: true,
        } as PayableStatementRecord,
        ...records.value,
      ];
    }
    return [
      {
        rowNo: '合计',
        businessDate: '',
        orderNo: '',
        summary: '',
        payableAmount: result.payableTotal || 0,
        paidAmount: result.paidTotal || 0,
        endingBalance: result.endingBalance || 0,
        isSummary: true,
      } as PayableDetailRecord,
      ...records.value,
    ];
  });

  const rowClass = (record: PayableRecord) =>
    record.isSummary ? 'summary-row' : '';

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const resetResult = () => {
    records.value = [];
    result.supplierId = undefined;
    result.supplierName = undefined;
    result.openingBalance = 0;
    result.payableTotal = 0;
    result.paidTotal = 0;
    result.endingBalance = 0;
    result.openingTotal = 0;
    result.endingTotal = 0;
  };

  const resolveSupplierId = async () => {
    const value = String(form.supplierId ?? '').trim();
    if (!value) return undefined;
    if (/^\d+$/.test(value)) return Number(value);

    const { data } = await getSupplierList(value);
    const suppliers = data || [];
    const supplier = suppliers.find((item) => item.name === value);
    if (supplier?.id !== undefined && supplier.id !== null) {
      return supplier.id;
    }
    return suppliers.length === 1 ? suppliers[0].id : undefined;
  };

  const search = async () => {
    if (!isStatistics.value && !form.supplierId) {
      resetResult();
      Message.warning('请选择要查询的供应商');
      return;
    }
    loading.value = true;
    try {
      const supplierId = form.supplierId
        ? await resolveSupplierId()
        : undefined;
      if (
        form.supplierId &&
        (supplierId === undefined || supplierId === null)
      ) {
        resetResult();
        Message.warning('璇烽€夋嫨鏈夋晥鐨勪緵搴斿晢');
        return;
      }
      const query = { ...form, supplierId };
      if (isStatement.value) {
        const { data } = await listPayableStatement(query);
        Object.assign(result, data || {});
        records.value = data?.records || [];
      } else if (isStatistics.value) {
        const { data } = await listPayableStatistics(query);
        Object.assign(result, data || {});
        records.value = data?.records || [];
      } else {
        const { data } = await listPayableDetail(query);
        Object.assign(result, data || {});
        records.value = data?.records || [];
      }
      if (result.supplierName) {
        form.supplierId = result.supplierName;
      }
    } finally {
      loading.value = false;
    }
  };

  const reset = () => {
    form.supplierId = undefined;
    timeSelectRef.value?.setPreset(2);
    if (isStatistics.value) {
      search();
    } else {
      resetResult();
    }
  };

  const exportFields = computed<Array<keyof PayableRecord>>(() => {
    if (isStatement.value) {
      return [
        'rowNo',
        'goodsName',
        'unit',
        'quantity',
        'unitPrice',
        'freightAmount',
        'totalAmount',
        'discountAmount',
        'note',
        'payableAmount',
        'paidAmount',
        'roundingAmount',
        'endingBalance',
      ] as Array<keyof PayableRecord>;
    }
    if (isStatistics.value) {
      return [
        'rowNo',
        'supplierName',
        'openingBalance',
        'payableAmount',
        'paidAmount',
        'roundingAmount',
        'endingBalance',
      ] as Array<keyof PayableRecord>;
    }
    return [
      'rowNo',
      'businessDate',
      'orderNo',
      'summary',
      'payableAmount',
      'paidAmount',
      'endingBalance',
    ] as Array<keyof PayableRecord>;
  });

  const csvValue = (item: PayableRecord, field: keyof PayableRecord) =>
    (item as any)[field] ?? '';

  const exportCsv = () => {
    if (!tableData.value.length) return;
    const headers = columns.value
      .filter((item) => item.dataIndex !== 'operation')
      .map((item) => item.title)
      .join(',');
    const rows = tableData.value.map((item) =>
      exportFields.value
        .map(
          (field) => `"${String(csvValue(item, field)).replace(/"/g, '""')}"`
        )
        .join(',')
    );
    const blob = new Blob([`\uFEFF${[headers, ...rows].join('\n')}`], {
      type: 'text/csv;charset=utf-8;',
    });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `${pageTitle.value}_${result.supplierName || ''}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const escapeHtml = (value: unknown) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');

  const printableValue = (item: PayableRecord, field: keyof PayableRecord) => {
    if (
      [
        'unitPrice',
        'freightAmount',
        'totalAmount',
        'discountAmount',
        'openingBalance',
        'payableAmount',
        'paidAmount',
        'endingBalance',
      ].includes(field)
    ) {
      const value = (item as any)[field] as number | undefined;
      return value == null ? '' : formatPlainAmount(value);
    }
    return escapeHtml((item as any)[field]);
  };

  const print = () => {
    if (!tableData.value.length) return;
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      Message.warning('浏览器阻止了打印窗口，请允许弹窗后重试');
      return;
    }

    const printColumns = columns.value.filter(
      (item) => item.dataIndex !== 'operation'
    );
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            ${exportFields.value
              .map((field) => {
                const isAmount = [
                  'unitPrice',
                  'freightAmount',
                  'totalAmount',
                  'discountAmount',
                  'openingBalance',
                  'payableAmount',
                  'paidAmount',
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
          <title>${pageTitle.value}</title>
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
          <h1>${escapeHtml(printTitle.value)}</h1>
          <table>
            <thead><tr>${printColumns
              .map((column) => `<th>${column.title}</th>`)
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
    const { supplierId, startDate, endDate } = route.query;
    if (typeof startDate === 'string' && typeof endDate === 'string') {
      form.startDate = startDate;
      form.endDate = endDate;
      timeSelectRef.value?.setRange([startDate, endDate]);
    }
    if (typeof supplierId === 'string' && supplierId) {
      const parsedSupplierId = Number(supplierId);
      form.supplierId = Number.isNaN(parsedSupplierId)
        ? supplierId
        : parsedSupplierId;
      search();
      return;
    }
    if (isStatistics.value) {
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

  .danger-amount {
    color: rgb(var(--red-6));
  }
</style>
