<template>
  <div class="container">
    <a-card class="general-card" title="进货统计报告" style="margin-top: 12px">
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
              <a-col v-if="isRoot" :span="6">
                <a-form-item field="cashierId" label="员工">
                  <user-select
                    v-model:user-id="form.cashierId"
                    placeholder="全部员工"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="isRoot ? 6 : 8">
                <a-form-item field="categoryId" label="分类">
                  <category-select-tree
                    v-model:category-id="form.categoryId"
                    v-model:category-text="categoryText"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="isRoot ? 6 : 8">
                <a-form-item field="goodsKey" label="货品名称">
                  <a-auto-complete
                    v-model="form.goodsKey"
                    :data="goodsSearchData"
                    :filter-option="() => true"
                    allow-clear
                    placeholder="货品名称 / 货品代码 / 拼音首字母"
                    @search="handleGoodsSearch"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="isRoot ? 6 : 8">
                <a-form-item field="supplierId" label="供应商">
                  <supplier-select
                    v-model:supplier-id="form.supplierId"
                    placeholder="全部供应商"
                  />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="24">
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
        <a-divider style="height: 124px" direction="vertical" />
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
        :scroll="{ x: 920, y: 560 }"
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
      width="70vw"
      :footer="false"
      :body-style="{ minHeight: 'calc(100vh - 240px)' }"
      :mask-closable="false"
    >
      <template #title>
        <div class="detail-header">
          <span>{{ detailTitle }}</span>
          <span class="detail-date-range">
            {{ form.startDate || '-' }} 至 {{ form.endDate || '-' }}
          </span>
        </div>
      </template>
      <div class="detail-toolbar">
        <a-space>
          <a-button @click="detailVisible = false">返回</a-button>
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
          <a-button :disabled="!detailRecords.length" @click="exportDetailCsv">
            <template #icon><icon-download /></template>
            导出
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
        :scroll="{ x: 1220, y: 'calc(100vh - 370px)' }"
        :row-class="detailRowClass"
      />
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import { useUserStore } from '@/store';
  import UserSelect from '@/views/app/AppUser/components/UserSelectModel.vue';
  import SupplierSelect from '@/views/app/AppSupplier/components/SupplierSelectModal.vue';
  import { searchKey } from '@/views/app/goods/api/api-AppGoods';
  import CategorySelectTree from '@/views/app/goods/components/category-select-tree.vue';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import {
    listPurchaseStatistics,
    listPurchaseStatisticsDetail,
    PurchaseStatisticsDetail,
    PurchaseStatisticsRecord,
    PurchaseStatisticsResult,
  } from './api';

  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const loading = ref(false);
  const records = ref<PurchaseStatisticsRecord[]>([]);
  const result = reactive<PurchaseStatisticsResult>({});
  const categoryText = ref('');
  const goodsSearchData = ref<GoodsSearchResult[]>([]);
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailGoodsName = ref('');
  const detailGoodsUnit = ref('');
  const detailRecords = ref<PurchaseStatisticsDetail[]>([]);
  const form = reactive({
    cashierId: undefined as number | undefined,
    categoryId: undefined as number | undefined,
    goodsKey: '',
    supplierId: undefined as number | string | undefined,
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });

  const quantityCell =
    (field: keyof PurchaseStatisticsRecord) => (record: any) =>
      h('span', null, Number(record.record[field] || 0).toString());
  const amountClass = (record: PurchaseStatisticsRecord, value: number) => {
    if (record.isSummary) return 'summary-amount';
    return value < 0 ? 'danger-amount' : undefined;
  };
  const amountCell =
    (field: keyof PurchaseStatisticsRecord) => (record: any) => {
      const row = record.record as PurchaseStatisticsRecord;
      const value = Number(row[field] || 0);
      return h(
        'span',
        {
          class: amountClass(row, value),
        },
        `¥${formatPrice(value)}`
      );
    };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '品名规格',
      dataIndex: 'goodsName',
      minWidth: 320,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    { title: '单位', dataIndex: 'unit', width: 90, align: 'center' },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 200,
      align: 'right',
      render: quantityCell('quantity'),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 220,
      align: 'right',
      render: amountCell('amount'),
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 100,
      align: 'center',
    },
  ];

  const detailMoneyCell =
    (field: keyof PurchaseStatisticsDetail) => (record: any) =>
      h('span', null, `¥${formatPrice(Number(record.record[field] || 0))}`);
  const detailColumns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 70, align: 'center' },
    {
      title: '进货日期',
      dataIndex: 'businessDate',
      width: 110,
      align: 'center',
    },
    { title: '单据编号', dataIndex: 'orderNo', width: 150, align: 'center' },
    {
      title: '供应商',
      dataIndex: 'supplierName',
      minWidth: 150,
      align: 'left',
    },
    { title: '品名规格', dataIndex: 'goodsName', minWidth: 200, align: 'left' },
    { title: '单位', dataIndex: 'unit', width: 70, align: 'center' },
    {
      title: '数量',
      dataIndex: 'quantity',
      width: 90,
      align: 'right',
      render: (record: any) => Number(record.record.quantity || 0).toFixed(2),
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      width: 120,
      align: 'right',
      render: detailMoneyCell('unitPrice'),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: detailMoneyCell('amount'),
    },
    {
      title: '折扣额',
      dataIndex: 'discountAmount',
      width: 120,
      align: 'right',
      render: detailMoneyCell('discountAmount'),
    },
    {
      title: '应付金额',
      dataIndex: 'payableAmount',
      width: 135,
      align: 'right',
      render: detailMoneyCell('payableAmount'),
    },
    {
      title: '备注',
      dataIndex: 'note',
      width: 140,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
  ];

  const tableData = computed<PurchaseStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        goodsName: '',
        unit: '',
        quantity: result.quantityTotal || 0,
        amount: result.amountTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });
  const rowClass = (record: PurchaseStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';
  const detailTitle = computed(() => {
    const supplierText = form.supplierId ? '已选供应商' : '全部供应商';
    const categoryLabel = form.categoryId
      ? categoryText.value || '已选分类'
      : '全部类别';
    return `进货统计报告 > ${supplierText} > ${categoryLabel} > ${detailGoodsName.value} 明细表`;
  });
  const detailTableData = computed<PurchaseStatisticsDetail[]>(() => {
    if (!detailRecords.value.length) return [];
    const totals = detailRecords.value.reduce<{
      quantity: number;
      amount: number;
      discountAmount: number;
      payableAmount: number;
    }>(
      (summary, record) => ({
        quantity: summary.quantity + Number(record.quantity || 0),
        amount: summary.amount + Number(record.amount || 0),
        discountAmount:
          summary.discountAmount + Number(record.discountAmount || 0),
        payableAmount:
          summary.payableAmount + Number(record.payableAmount || 0),
      }),
      { quantity: 0, amount: 0, discountAmount: 0, payableAmount: 0 }
    );
    return [
      ...detailRecords.value.map((record) => ({
        ...record,
        goodsName: detailGoodsName.value,
        unit: detailGoodsUnit.value,
      })),
      {
        rowNo: '小计',
        quantity: totals.quantity,
        amount: totals.amount,
        discountAmount: totals.discountAmount,
        payableAmount: totals.payableAmount,
        isSummary: true,
      },
    ];
  });
  const detailRowClass = (record: PurchaseStatisticsDetail) =>
    record.isSummary ? 'detail-summary-row' : '';

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listPurchaseStatistics({
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
    form.cashierId = undefined;
    form.categoryId = undefined;
    categoryText.value = '';
    form.goodsKey = '';
    form.supplierId = undefined;
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
  const handleGoodsSearch = async (key: string) => {
    if (!key) {
      goodsSearchData.value = [];
      return;
    }
    const { data } = await searchKey(key);
    goodsSearchData.value = data || [];
  };
  const showDetail = async (record: PurchaseStatisticsRecord) => {
    if (!record.goodsId) return;
    detailGoodsName.value = record.goodsName || '';
    detailGoodsUnit.value = record.unit || '';
    detailRecords.value = [];
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const { data } = await listPurchaseStatisticsDetail({
        ...form,
        goodsId: record.goodsId,
      });
      detailRecords.value = data || [];
    } finally {
      detailLoading.value = false;
    }
  };

  const csvValue = (value: unknown) =>
    `"${String(value ?? '').replace(/"/g, '""')}"`;
  const downloadCsv = (name: string, headers: string[], rows: unknown[][]) => {
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
    link.download = name;
    link.click();
    URL.revokeObjectURL(link.href);
  };
  const exportCsv = () => {
    downloadCsv(
      '进货统计报告.csv',
      ['行号', '品名规格', '单位', '数量', '金额'],
      tableData.value.map((item) => [
        item.rowNo,
        item.goodsName,
        item.unit,
        item.quantity,
        item.amount,
      ])
    );
  };
  const exportDetailCsv = () => {
    downloadCsv(
      `${detailGoodsName.value || '进货'}明细.csv`,
      [
        '行号',
        '进货日期',
        '单据编号',
        '供应商',
        '品名规格',
        '单位',
        '数量',
        '单价',
        '金额',
        '折扣额',
        '应付金额',
        '备注',
      ],
      detailTableData.value.map((item) => [
        item.rowNo,
        item.businessDate,
        item.orderNo,
        item.supplierName,
        item.goodsName,
        item.unit,
        item.quantity,
        item.unitPrice,
        item.amount,
        item.discountAmount,
        item.payableAmount,
        item.note,
      ])
    );
  };

  const escapeHtml = (value: unknown) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  const writePrintDocument = (
    title: string,
    headers: string[],
    rows: string[][],
    autoPrint: boolean
  ) => {
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>${escapeHtml(title)}</title>
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
          <h1>${escapeHtml(title)}</h1>
          <table>
            <thead><tr>${headers
              .map((header) => `<th>${escapeHtml(header)}</th>`)
              .join('')}</tr></thead>
            <tbody>${rows
              .map(
                (row) =>
                  `<tr>${row
                    .map(
                      (cell, index) =>
                        `<td${index > 5 ? ' class="amount"' : ''}>${escapeHtml(
                          cell
                        )}</td>`
                    )
                    .join('')}</tr>`
              )
              .join('')}</tbody>
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
  const print = (autoPrint: boolean) => {
    writePrintDocument(
      '进货统计报告',
      ['行号', '品名规格', '单位', '数量', '金额'],
      tableData.value.map((item) => [
        String(item.rowNo ?? ''),
        String(item.goodsName ?? ''),
        String(item.unit ?? ''),
        String(item.quantity ?? 0),
        `¥${formatPrice(Number(item.amount || 0))}`,
      ]),
      autoPrint
    );
  };
  const printDetail = (autoPrint: boolean) => {
    writePrintDocument(
      detailTitle.value,
      [
        '行号',
        '进货日期',
        '单据编号',
        '供应商',
        '品名规格',
        '单位',
        '数量',
        '单价',
        '金额',
        '折扣额',
        '应付金额',
        '备注',
      ],
      detailTableData.value.map((item) => [
        String(item.rowNo ?? ''),
        String(item.businessDate ?? ''),
        String(item.orderNo ?? ''),
        String(item.supplierName ?? ''),
        String(item.goodsName ?? ''),
        String(item.unit ?? ''),
        Number(item.quantity || 0).toFixed(2),
        `¥${formatPrice(Number(item.unitPrice || 0))}`,
        `¥${formatPrice(Number(item.amount || 0))}`,
        `¥${formatPrice(Number(item.discountAmount || 0))}`,
        `¥${formatPrice(Number(item.payableAmount || 0))}`,
        String(item.note ?? ''),
      ]),
      autoPrint
    );
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

  .detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .detail-date-range {
    flex: none;
    color: var(--color-text-2);
    font-size: 13px;
    font-weight: 400;
  }

  .detail-toolbar {
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--color-border-2);
  }

  :deep(.detail-summary-row) {
    font-weight: 600;
    color: rgb(var(--arcoblue-6));
    background: var(--color-fill-2);
  }

  .danger-amount {
    color: rgb(var(--red-6));
  }
</style>
