<template>
  <div class="container">
    <a-card class="general-card" title="库存统计报告" style="margin-top: 12px">
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
                <a-form-item field="categoryId" label="货品分类">
                  <category-select-tree
                    v-model:category-id="form.categoryId"
                    v-model:category-text="categoryText"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item field="key" label="货品">
                  <a-auto-complete
                    v-model="form.key"
                    :data="searchData"
                    :filter-option="() => true"
                    allow-clear
                    placeholder="货品名称 / 货品代码 / 拼音首字母"
                    @search="handleSearchKey"
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

    <a-card class="general-card report-card" :bordered="false">
      <a-table
        row-key="rowNo"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data="tableData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1230, y: 560 }"
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

    <stock-detail-modal ref="stockDetailRef" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import CategorySelectTree from '@/views/app/goods/components/category-select-tree.vue';
  import StockDetailModal from '@/views/app/goods/components/stock-detail-modal.vue';
  import { searchKey } from '@/views/app/goods/api/api-AppGoods';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import type { AppGoods } from '@/views/app/goods/types/AppGoods';
  import {
    listStockStatistics,
    StockStatisticsRecord,
    StockStatisticsResult,
  } from './api';

  const loading = ref(false);
  const records = ref<StockStatisticsRecord[]>([]);
  const result = reactive<StockStatisticsResult>({});
  const categoryText = ref('');
  const searchData = ref<GoodsSearchResult[]>([]);
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const stockDetailRef = ref<InstanceType<typeof StockDetailModal> | null>(
    null
  );
  const form = reactive({
    categoryId: undefined as number | undefined,
    key: '',
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });

  const amountClass = (value: number) =>
    value < 0 ? 'danger-amount' : undefined;

  const numberCell = (field: keyof StockStatisticsRecord) => (record: any) => {
    const row = record.record as StockStatisticsRecord;
    const value = Number(row[field] || 0);
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : amountClass(value) },
      value.toString()
    );
  };

  const amountCell = (field: keyof StockStatisticsRecord) => (record: any) => {
    const row = record.record as StockStatisticsRecord;
    const value = Number(row[field] || 0);
    return h(
      'span',
      { class: row.isSummary ? 'summary-amount' : amountClass(value) },
      `￥${formatPrice(value)}`
    );
  };

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 72, align: 'center' },
    {
      title: '品名规格',
      dataIndex: 'goodsName',
      width: 280,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    { title: '单位', dataIndex: 'unit', width: 74, align: 'center' },
    {
      title: '期初结存',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'openingQty',
          width: 100,
          align: 'right',
          render: numberCell('openingQty'),
        },
        {
          title: '金额',
          dataIndex: 'openingAmount',
          width: 120,
          align: 'right',
          render: amountCell('openingAmount'),
        },
      ],
    },
    {
      title: '库存新增',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'inQty',
          width: 100,
          align: 'right',
          render: numberCell('inQty'),
        },
        {
          title: '金额',
          dataIndex: 'inAmount',
          width: 120,
          align: 'right',
          render: amountCell('inAmount'),
        },
      ],
    },
    {
      title: '库存减少（成本）',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'outQty',
          width: 100,
          align: 'right',
          render: numberCell('outQty'),
        },
        {
          title: '金额',
          dataIndex: 'outAmount',
          width: 120,
          align: 'right',
          render: amountCell('outAmount'),
        },
      ],
    },
    {
      title: '期末结存',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'endingQty',
          width: 100,
          align: 'right',
          render: numberCell('endingQty'),
        },
        {
          title: '金额',
          dataIndex: 'endingAmount',
          width: 120,
          align: 'right',
          render: amountCell('endingAmount'),
        },
      ],
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 80,
      align: 'center',
    },
  ];

  const tableData = computed<StockStatisticsRecord[]>(() => {
    if (!records.value.length) return [];
    return [
      {
        rowNo: '合计',
        goodsName: '',
        unit: '',
        openingQty: result.openingQtyTotal || 0,
        openingAmount: result.openingAmountTotal || 0,
        inQty: result.inQtyTotal || 0,
        inAmount: result.inAmountTotal || 0,
        outQty: result.outQtyTotal || 0,
        outAmount: result.outAmountTotal || 0,
        endingQty: result.endingQtyTotal || 0,
        endingAmount: result.endingAmountTotal || 0,
        isSummary: true,
      },
      ...records.value,
    ];
  });

  const rowClass = (record: StockStatisticsRecord) =>
    record.isSummary ? 'summary-row' : '';

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listStockStatistics({
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
    form.categoryId = undefined;
    categoryText.value = '';
    form.key = '';
    timeSelectRef.value?.setPreset(2);
    search();
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

  const handleSearchKey = async (key: string) => {
    if (!key) {
      searchData.value = [];
      return;
    }
    const { data } = await searchKey(key);
    searchData.value = data || [];
  };

  const showDetail = (record: StockStatisticsRecord) => {
    if (!record.goodsId) return;
    const goods: AppGoods = {
      id: record.goodsId,
      title: record.goodsName,
      unit: record.unit,
    };
    stockDetailRef.value?.showModal(goods);
  };

  const csvValue = (
    item: StockStatisticsRecord,
    field: keyof StockStatisticsRecord
  ) => item[field] ?? '';

  const exportCsv = () => {
    const fields: Array<keyof StockStatisticsRecord> = [
      'rowNo',
      'goodsName',
      'unit',
      'openingQty',
      'openingAmount',
      'inQty',
      'inAmount',
      'outQty',
      'outAmount',
      'endingQty',
      'endingAmount',
    ];
    const headers = [
      '行号',
      '品名规格',
      '单位',
      '期初数量',
      '期初金额',
      '新增数量',
      '新增金额',
      '减少数量',
      '减少金额',
      '期末数量',
      '期末金额',
    ];
    const rows = tableData.value.map((item) =>
      fields
        .map(
          (field) => `"${String(csvValue(item, field)).replace(/"/g, '""')}"`
        )
        .join(',')
    );
    const blob = new Blob(
      [`\uFEFF${[headers.join(','), ...rows].join('\n')}`],
      {
        type: 'text/csv;charset=utf-8;',
      }
    );
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = '库存统计报告.csv';
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
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    const headers = [
      '行号',
      '品名规格',
      '单位',
      '期初数量',
      '期初金额',
      '新增数量',
      '新增金额',
      '减少数量',
      '减少金额',
      '期末数量',
      '期末金额',
    ];
    const rows = tableData.value
      .map(
        (item) => `
          <tr>
            <td>${escapeHtml(item.rowNo)}</td>
            <td>${escapeHtml(item.goodsName)}</td>
            <td>${escapeHtml(item.unit)}</td>
            <td class="amount">${escapeHtml(item.openingQty)}</td>
            <td class="amount">￥${formatPrice(item.openingAmount || 0)}</td>
            <td class="amount">${escapeHtml(item.inQty)}</td>
            <td class="amount">￥${formatPrice(item.inAmount || 0)}</td>
            <td class="amount">${escapeHtml(item.outQty)}</td>
            <td class="amount">￥${formatPrice(item.outAmount || 0)}</td>
            <td class="amount">${escapeHtml(item.endingQty)}</td>
            <td class="amount">￥${formatPrice(item.endingAmount || 0)}</td>
          </tr>`
      )
      .join('');
    printWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>库存统计报告</title>
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
          <h1>库存统计报告</h1>
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

  .danger-amount {
    color: rgb(var(--red-6));
  }
</style>
