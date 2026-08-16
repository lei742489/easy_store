<template>
  <div class="container">
    <a-card class="general-card" title="库存预警报告" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form :model="form" auto-label-width layout="inline">
            <a-form-item field="supplierId" label="供应商">
              <supplier-select
                v-model:supplier-id="form.supplierId"
                placeholder="全部供应商"
              />
            </a-form-item>
          </a-form>
        </a-col>
        <a-divider style="height: 48px" direction="vertical" />
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
      <a-divider style="margin-top: 12px" />
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-space>
            <a-button :disabled="!renderData.length" @click="exportCsv">
              <template #icon><icon-download /></template>
              导出
            </a-button>
            <a-button
              v-print="'#stock-warning-table'"
              :disabled="!renderData.length"
            >
              <template #icon><icon-printer /></template>
              打印
            </a-button>
            <a-button :disabled="!renderData.length" @click="preview">
              预览
            </a-button>
            <a-button
              type="primary"
              :disabled="!selectedRowKeys.length"
              @click="generatePurchaseOrder"
            >
              生成进货单
            </a-button>
          </a-space>
        </a-col>
      </a-row>
    </a-card>

    <a-card class="general-card" :bordered="false">
      <a-table
        id="stock-warning-table"
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        :columns="columns"
        :data="renderData"
        :bordered="{ cell: true }"
        :scroll="{ x: 1040, y: 560 }"
        :row-selection="rowSelection"
        @selection-change="handleSelectionChange"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #operations="{ record }">
          <a-button type="text" size="small" @click="showDetail(record)">
            明细
          </a-button>
        </template>
      </a-table>
    </a-card>

    <stock-detail-modal ref="stockDetailRef" />
    <purchase-order-modal ref="purchaseOrderModalRef" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import SupplierSelect from '@/views/app/AppSupplier/components/SupplierSelectModal.vue';
  import PurchaseOrderModal from '@/views/app/AppPurchaseOrder/components/modal.vue';
  import type { AppPurchaseOrder } from '@/views/app/AppPurchaseOrder/types/AppPurchaseOrder';
  import type { AppGoods } from '@/views/app/goods/types/AppGoods';
  import StockDetailModal from '@/views/app/goods/components/stock-detail-modal.vue';
  import { listStockWarnings } from './api';

  const loading = ref(false);
  const renderData = ref<AppGoods[]>([]);
  const selectedRowKeys = ref<Array<number | string>>([]);
  const stockDetailRef = ref<InstanceType<typeof StockDetailModal> | null>(
    null
  );
  const purchaseOrderModalRef = ref<InstanceType<
    typeof PurchaseOrderModal
  > | null>(null);
  const form = reactive({
    supplierId: undefined as number | string | undefined,
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
  });
  const rowSelection = reactive({
    selectedRowKeys,
    type: 'checkbox',
    showCheckedAll: true,
  });

  const numberValue = (value?: number) => Number(value || 0);
  const warningQuantity = (record: AppGoods) => {
    const stock = numberValue(record.stock);
    const minStock = numberValue(record.minStock);
    const maxStock = numberValue(record.maxStock);
    if (stock < minStock) return minStock - stock;
    return maxStock > 0 && stock > maxStock ? stock - maxStock : 0;
  };

  const warningCell = (record: any) =>
    h('span', { class: 'warning-amount' }, warningQuantity(record.record));
  const quantityCell = (field: keyof AppGoods) => (record: any) =>
    h('span', { class: 'warning-amount' }, numberValue(record.record[field]));

  const columns: TableColumnData[] = [
    {
      title: '行号',
      dataIndex: 'index',
      slotName: 'index',
      width: 72,
      align: 'center',
    },
    {
      title: '品名规格',
      dataIndex: 'title',
      width: 320,
      align: 'left',
      ellipsis: true,
      tooltip: true,
    },
    { title: '单位', dataIndex: 'unit', width: 80, align: 'center' },
    {
      title: '最高存量',
      dataIndex: 'maxStock',
      width: 130,
      align: 'right',
      render: quantityCell('maxStock'),
    },
    {
      title: '最低存量',
      dataIndex: 'minStock',
      width: 130,
      align: 'right',
      render: quantityCell('minStock'),
    },
    {
      title: '当前存量',
      dataIndex: 'stock',
      width: 130,
      align: 'right',
      render: quantityCell('stock'),
    },
    {
      title: '超出/短缺存货数量',
      dataIndex: 'warningQuantity',
      width: 170,
      align: 'right',
      render: warningCell,
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 80,
      align: 'center',
    },
  ];

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listStockWarnings({
        ...form,
        current: pagination.current,
        pageSize: pagination.pageSize,
      });
      renderData.value = data?.records || [];
      pagination.current = data?.current || 1;
      pagination.total = data?.total || 0;
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    selectedRowKeys.value = [];
    pagination.current = 1;
    fetchData();
  };

  const reset = () => {
    form.supplierId = undefined;
    search();
  };

  const onPageChange = (current: number) => {
    selectedRowKeys.value = [];
    pagination.current = current;
    fetchData();
  };

  const onPageSizeChange = (pageSize: number) => {
    selectedRowKeys.value = [];
    pagination.current = 1;
    pagination.pageSize = pageSize;
    fetchData();
  };

  const handleSelectionChange = (keys: Array<number | string>) => {
    selectedRowKeys.value = keys;
  };

  const selectedRecords = computed(() => {
    const ids = new Set(selectedRowKeys.value.map((id) => String(id)));
    return renderData.value.filter((item) => ids.has(String(item.id)));
  });

  const showDetail = (record: AppGoods) => {
    stockDetailRef.value?.showModal(record);
  };

  const generatePurchaseOrder = () => {
    const shortages = selectedRecords.value.filter(
      (item) => numberValue(item.stock) < numberValue(item.minStock)
    );
    if (!shortages.length) {
      Message.warning('请选择需要补货的商品');
      return;
    }
    const [firstShortage] = shortages;
    const { supplierId } = firstShortage;
    if (
      !supplierId ||
      shortages.some((item) => String(item.supplierId) !== String(supplierId))
    ) {
      Message.warning('生成进货单的商品必须设置同一供应商');
      return;
    }
    const order: AppPurchaseOrder = {
      supplierId,
      items: shortages.map((item) => ({
        goodsId: item.id,
        goodsName: item.title,
        categoryId: item.categoryId,
        unit: item.unit,
        quantity: warningQuantity(item),
        unitPrice: 0,
        totalAmount: 0,
      })),
    };
    purchaseOrderModalRef.value?.showModal(order);
  };

  const exportCsv = () => {
    const headers = [
      '行号',
      '品名规格',
      '单位',
      '最高存量',
      '最低存量',
      '当前存量',
      '超出/短缺存货数量',
    ];
    const rows = renderData.value.map((item, index) =>
      [
        index + 1 + (pagination.current - 1) * pagination.pageSize,
        item.title,
        item.unit,
        numberValue(item.maxStock),
        numberValue(item.minStock),
        numberValue(item.stock),
        warningQuantity(item),
      ]
        .map((value) => `"${String(value ?? '').replace(/"/g, '""')}"`)
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
    link.download = '库存预警报告.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const preview = () => {
    const previewWindow = window.open('', '_blank');
    if (!previewWindow) return;
    const rows = renderData.value
      .map(
        (item, index) => `
          <tr>
            <td>${
              index + 1 + (pagination.current - 1) * pagination.pageSize
            }</td>
            <td>${item.title || ''}</td>
            <td>${item.unit || ''}</td>
            <td>${numberValue(item.maxStock)}</td>
            <td>${numberValue(item.minStock)}</td>
            <td>${numberValue(item.stock)}</td>
            <td>${warningQuantity(item)}</td>
          </tr>`
      )
      .join('');
    previewWindow.document.write(`
      <!doctype html>
      <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <title>库存预警报告</title>
          <style>
            body { margin: 24px; color: #1d2129; font-family: Arial, "Microsoft YaHei", sans-serif; }
            h1 { text-align: center; font-size: 20px; }
            table { width: 100%; border-collapse: collapse; font-size: 13px; }
            th, td { padding: 8px; border: 1px solid #c9cdd4; text-align: center; }
            th { background: #f2f3f5; }
          </style>
        </head>
        <body>
          <h1>库存预警报告</h1>
          <table>
            <thead><tr><th>行号</th><th>品名规格</th><th>单位</th><th>最高存量</th><th>最低存量</th><th>当前存量</th><th>超出/短缺存货数量</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
        </body>
      </html>
    `);
    previewWindow.document.close();
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

  .warning-amount {
    color: rgb(var(--red-6));
  }
</style>
