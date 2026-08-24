<template>
  <a-modal
    :visible="visible"
    width="80%"
    :footer="false"
    :body-style="{ minHeight: '500px' }"
    unmount-on-close
    :mask-closable="true"
    @cancel="handleCancel"
  >
    <template #title>库存明细 - {{ result.goodsName || goodsName }}</template>

    <div class="filter-bar">
      <a-form :model="form" auto-label-width layout="inline">
        <a-form-item field="businessDate" label="日期">
          <time-select
            ref="timeSelectRef"
            :default-time-idx="2"
            @change="timeSelectChange"
          />
        </a-form-item>
      </a-form>
      <a-space>
        <a-button type="primary" :loading="loading" @click="search">
          <template #icon><icon-refresh /></template>
          查询
        </a-button>
        <a-button
          :loading="rebuildLoading"
          :disabled="!goodsId"
          @click="handleRebuild"
        >
          <template #icon><icon-refresh /></template>
          重新核算
        </a-button>
      </a-space>
    </div>

    <div class="report-title">
      <span>货品：{{ result.goodsName || goodsName }}</span>
      <span v-if="result.unit || goodsUnit">
        单位：{{ result.unit || goodsUnit }}
      </span>
    </div>

    <a-table
      row-key="rowNo"
      :loading="loading"
      :pagination="false"
      :columns="columns"
      :data="tableData"
      :bordered="{ cell: true }"
      :scroll="{ x: 1230, y: 520 }"
      :row-class="rowClass"
      :span-method="spanMethod"
    >
      <template #inQty="{ record }">
        {{ formatQuantity(record.inQty) }}
      </template>
      <template #inUnitPrice="{ record }">
        {{ formatMoney(record.inUnitPrice) }}
      </template>
      <template #inAmount="{ record }">
        <span :class="amountClass(record)">{{
          formatMoney(record.inAmount)
        }}</span>
      </template>
      <template #outQty="{ record }">
        {{ formatQuantity(record.outQty) }}
      </template>
      <template #outCostPrice="{ record }">
        {{ formatMoney(record.outCostPrice) }}
      </template>
      <template #outAmount="{ record }">
        <span :class="amountClass(record)">{{
          formatMoney(record.outAmount)
        }}</span>
      </template>
      <template #endingQty="{ record }">
        {{ formatQuantity(record.endingQty) }}
      </template>
      <template #endingCostPrice="{ record }">
        {{ formatMoney(record.endingCostPrice) }}
      </template>
      <template #endingAmount="{ record }">
        <span :class="amountClass(record)">{{
          formatMoney(record.endingAmount)
        }}</span>
      </template>
    </a-table>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import type {
    TableColumnData,
    TableOperationColumn,
  } from '@arco-design/web-vue/es/table/interface';
  import dayjs from 'dayjs';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import type { AppGoods } from '../types/AppGoods';
  import { rebuildStockLedger, stockDetail } from '../api/api-AppGoods';

  interface StockDetailRecord {
    rowNo: number | string;
    businessType?: string;
    businessDate?: string;
    counterpartyName?: string;
    inQty?: number;
    inUnitPrice?: number;
    inAmount?: number;
    outQty?: number;
    outCostPrice?: number;
    outAmount?: number;
    endingQty?: number;
    endingCostPrice?: number;
    endingAmount?: number;
    isSummary?: boolean;
    isOpening?: boolean;
  }

  interface StockDetailResult {
    goodsId?: number;
    goodsName?: string;
    unit?: string;
    openingQty?: number;
    openingCostPrice?: number;
    openingAmount?: number;
    inQtyTotal?: number;
    inTotal?: number;
    outQtyTotal?: number;
    outTotal?: number;
    endingQty?: number;
    endingCostPrice?: number;
    endingAmount?: number;
    records?: StockDetailRecord[];
  }

  const mergeColumns = ['businessType', 'businessDate', 'counterpartyName'];

  const visible = ref(false);
  const loading = ref(false);
  const rebuildLoading = ref(false);
  const goodsId = ref<number>();
  const goodsName = ref('');
  const goodsUnit = ref('');
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const records = ref<StockDetailRecord[]>([]);
  const result = reactive<StockDetailResult>({});
  const form = reactive({
    startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
    endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
  });

  const columns: TableColumnData[] = [
    { title: '行号', dataIndex: 'rowNo', width: 70, align: 'center' },
    {
      title: '业务类型',
      dataIndex: 'businessType',
      width: 95,
      align: 'center',
    },
    {
      title: '业务日期',
      dataIndex: 'businessDate',
      width: 112,
      align: 'center',
    },
    {
      title: '交易单位',
      dataIndex: 'counterpartyName',
      width: 150,
      align: 'center',
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '库存新增',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'inQty',
          width: 88,
          align: 'right',
          slotName: 'inQty',
        },
        {
          title: '单价',
          dataIndex: 'inUnitPrice',
          width: 105,
          align: 'right',
          slotName: 'inUnitPrice',
        },
        {
          title: '金额',
          dataIndex: 'inAmount',
          width: 112,
          align: 'right',
          slotName: 'inAmount',
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
          width: 88,
          align: 'right',
          slotName: 'outQty',
        },
        {
          title: '成本单价',
          dataIndex: 'outCostPrice',
          width: 105,
          align: 'right',
          slotName: 'outCostPrice',
        },
        {
          title: '金额',
          dataIndex: 'outAmount',
          width: 112,
          align: 'right',
          slotName: 'outAmount',
        },
      ],
    },
    {
      title: '期末结存（成本）',
      align: 'center',
      children: [
        {
          title: '数量',
          dataIndex: 'endingQty',
          width: 88,
          align: 'right',
          slotName: 'endingQty',
        },
        {
          title: '单价',
          dataIndex: 'endingCostPrice',
          width: 105,
          align: 'right',
          slotName: 'endingCostPrice',
        },
        {
          title: '金额',
          dataIndex: 'endingAmount',
          width: 112,
          align: 'right',
          slotName: 'endingAmount',
        },
      ],
    },
  ];

  const tableData = computed<StockDetailRecord[]>(() => [
    {
      rowNo: '合计',
      inQty: result.inQtyTotal || 0,
      inAmount: result.inTotal || 0,
      outQty: result.outQtyTotal || 0,
      outAmount: result.outTotal || 0,
      endingQty: result.endingQty || 0,
      endingCostPrice: result.endingCostPrice || 0,
      endingAmount: result.endingAmount || 0,
      isSummary: true,
    },
    {
      rowNo: 1,
      businessType: '期初',
      businessDate: form.startDate,
      endingQty: result.openingQty || 0,
      endingCostPrice: result.openingCostPrice || 0,
      endingAmount: result.openingAmount || 0,
      isOpening: true,
    },
    ...records.value.map((item, index) => ({
      ...item,
      businessType: formatBusinessType(item.businessType),
      rowNo: index + 2,
    })),
  ]);

  const formatBusinessType = (value?: string) => {
    const typeMap: Record<string, string> = {
      进货入库: '进货',
      销售出库: '销售',
      库存盘盈: '盘盈',
      库存盘亏: '盘亏',
    };
    return value ? typeMap[value] || value : '';
  };

  const sameMergeGroup = (
    current: StockDetailRecord,
    next: StockDetailRecord,
    fields: string[]
  ) =>
    fields.every(
      (field) =>
        (current as Record<string, unknown>)[field] ===
        (next as Record<string, unknown>)[field]
    );

  const buildMergeSpans = (
    rows: StockDetailRecord[],
    field: string,
    parentFields: string[] = []
  ) => {
    const spans: number[] = new Array(rows.length).fill(1);
    let index = 2;
    while (index < rows.length) {
      const current = rows[index] as Record<string, unknown>;
      const value = current[field];
      if (value === undefined || value === null || value === '') {
        spans[index] = 1;
        index += 1;
        continue;
      }

      let end = index + 1;
      while (end < rows.length) {
        const next = rows[end] as StockDetailRecord;
        if (
          !sameMergeGroup(rows[index], next, [...parentFields, field])
        ) {
          break;
        }
        end += 1;
      }
      spans[index] = end - index;
      for (let i = index + 1; i < end; i += 1) {
        spans[i] = 0;
      }
      index = end;
    }
    return spans;
  };

  const mergedSpanMap = computed<Record<string, number[]>>(() => {
    const rows = tableData.value;
    return {
      businessType: buildMergeSpans(rows, 'businessType'),
      businessDate: buildMergeSpans(rows, 'businessDate', ['businessType']),
      counterpartyName: buildMergeSpans(rows, 'counterpartyName', [
        'businessType',
        'businessDate',
      ]),
    };
  });

  const formatMoney = (value?: number) =>
    `￥${formatPrice(Number(value || 0))}`;
  const formatQuantity = (value?: number) =>
    value === undefined || value === null || value === 0
      ? ''
      : Number(value).toString();
  const amountClass = (record: StockDetailRecord) =>
    record.isSummary ? 'summary-amount' : '';
  const spanMethod = (data: {
    record: StockDetailRecord;
    column: TableColumnData | TableOperationColumn;
    rowIndex: number;
    columnIndex: number;
  }): void | { rowspan: number; colspan: number } => {
    if (data.rowIndex < 2) {
      return;
    }
    const field =
      'dataIndex' in data.column
        ? (data.column.dataIndex as string | undefined)
        : undefined;
    if (!field || !mergeColumns.includes(field)) {
      return;
    }
    const rowSpan = mergedSpanMap.value[field]?.[data.rowIndex] || 1;
    return rowSpan > 0 ? { rowspan: rowSpan, colspan: 1 } : { rowspan: 0, colspan: 0 };
  };
  const rowClass = (record: StockDetailRecord) => {
    if (record.isSummary) return 'summary-row';
    return record.isOpening ? 'opening-row' : '';
  };

  const timeSelectChange = (dates: string[]) => {
    [form.startDate, form.endDate] = dates;
  };

  const search = async () => {
    if (!goodsId.value) return;
    loading.value = true;
    try {
      const { data } = await stockDetail({
        goodsId: goodsId.value,
        ...form,
      });
      Object.assign(result, data || {});
      records.value = data?.records || [];
    } finally {
      loading.value = false;
    }
  };

  const handleRebuild = async () => {
    if (!goodsId.value) return;
    rebuildLoading.value = true;
    try {
      await rebuildStockLedger(goodsId.value);
      await search();
    } finally {
      rebuildLoading.value = false;
    }
  };

  const showModal = (goods: AppGoods) => {
    goodsId.value = goods.id;
    goodsName.value = goods.title || '';
    goodsUnit.value = goods.unit || '';
    form.startDate = dayjs().startOf('month').format('YYYY-MM-DD');
    form.endDate = dayjs().endOf('month').format('YYYY-MM-DD');
    records.value = [];
    Object.keys(result).forEach((key) => {
      delete (result as Record<string, unknown>)[key];
    });
    visible.value = true;
    nextTick(() => {
      timeSelectRef.value?.setPreset(2);
    });
    search();
  };

  const handleCancel = () => {
    visible.value = false;
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .filter-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 12px;
    margin-bottom: 14px;
    border-bottom: 1px solid var(--color-border-2);
  }

  .report-title {
    display: flex;
    gap: 24px;
    margin-bottom: 12px;
    color: var(--color-text-1);
    font-weight: 600;
  }

  :deep(.summary-row) {
    font-weight: 600;
    background: var(--color-fill-2);
  }

  :deep(.opening-row) {
    background: var(--color-fill-1);
  }

  .summary-amount {
    color: rgb(var(--arcoblue-6));
  }
</style>
