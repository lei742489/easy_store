<template>
  <div class="purchase-order-table">
    <a-table
      id="a-table"
      row-key="id"
      :loading="loading"
      :pagination="false"
      :columns="(columns as TableColumnData[])"
      :data="renderData"
      :bordered="{ cell: true }"
      size="small"
      :row-selection="orderId == undefined ? rowSelection : false"
      :scrollbar="true"
      :scroll="{ x: '100%', y: 240 }"
      :summary="renderData.length > 0"
      @selection-change="selectChange"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
      </template>

      <template #amount="{ rowIndex }">
        <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
          <a-input-number
            v-model="renderData[rowIndex].amount"
            :precision="2"
            :min="0"
            style="flex: 1; min-width: 0;"
          ></a-input-number>
          <a-button size="mini" type="outline" @click="fillAmount(rowIndex)">
            已付
          </a-button>
        </div>
      </template>

      <template #note="{ rowIndex }">
        <a-input v-model="renderData[rowIndex].note"></a-input>
      </template>

      <template #summary-cell="{ column, record }">
        <div v-if="column.dataIndex == 'createTime'">
          <div style="width: 100%; text-align: center">合计:</div>
        </div>

        <div
          v-if="
            column.dataIndex !== 'note' &&
            column.dataIndex !== 'orderNo' &&
            column.dataIndex !== 'createTime'
          "
          >￥{{ formatPrice(record[column.dataIndex]) }}</div
        >
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref, watch } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import { listPage } from '@/views/app/AppPurchaseOrder/api/api-AppPurchaseOrder';
  import type { AppPaymentAmountItem } from '../types/AppPaymentAmountItem';

  const { loading, setLoading } = useLoading(false);

  const renderData = ref<AppPaymentAmountItem[]>([]);
  const supplierId = defineModel<string>('supplierId');
  const orderId = defineModel<number>('orderId');
  const selectedRowKeys = ref<(string | number)[]>([]);

  const rowSelection = reactive({
    selectedRowKeys,
    type: 'checkbox',
    showCheckedAll: true,
  });

  const basePagination: Pagination = {
    current: 1,
    pageSize: 200,
    order: 'desc',
    column: 'createTime',
    showTotal: true,
  };

  const pagination = reactive({
    ...basePagination,
  });

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '\u65e5\u671f',
      dataIndex: 'createTime',
      align: 'center',
      render: (record) => {
        const data = record.record;
        const time = (data as any).createTime;
        return time !== undefined ? String(time).split(' ')[0] : '';
      },
    },
    {
      title: '单号',
      dataIndex: 'orderNo',
      align: 'center',
    },
    {
      title: '应付金额',
      dataIndex: 'payableAmount',
      align: 'center',
      render: (record) => {
        const data = record.record;
        return getPriceStrByH((data as any).payableAmount);
      },
    },
    {
      title: '已付金额',
      dataIndex: 'paidAmount',
      align: 'center',
      render: (record) => {
        const data = record.record;
        return getPriceStrByH((data as any).paidAmount);
      },
    },
    {
      title: '未付金额',
      dataIndex: 'unpaidAmount',
      align: 'center',
      render: (record) => {
        const data = record.record;
        return getPriceStrByH((data as any).unpaidAmount);
      },
    },
    {
      title: '本次付款',
      dataIndex: 'amount',
      align: 'center',
      slotName: 'amount',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      slotName: 'note',
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 200,
      order: 'desc',
      column: 'createTime',
    }
  ) => {
    setLoading(true);
    try {
      const { data } = await listPage(params);
      renderData.value = data.records!;
      pagination.total = data.total;
      pagination.current = data.current || 1;
    } finally {
      setLoading(false);
    }
  };

  const query = () => {
    if (!supplierId.value) return;
    const pg = {
      unpaidOnly: 1,
      supplierId: supplierId.value,
      orderId: orderId.value,
    };
    fetchData({
      ...pagination,
      ...pg,
    } as unknown as PolicyParams);
  };

  const selectChange = (e: (string | number)[]) => {
    const selectedKeys = new Set(e.map((key) => String(key)));
    renderData.value.forEach((item) => {
      if (!item.id) return;
      if (selectedKeys.has(String(item.id))) {
        item.amount = item.amount ?? 0;
      } else {
        item.amount = undefined;
      }
    });
    selectedRowKeys.value = e;
  };

  const fillAmount = (rowIndex: number) => {
    const row = renderData.value[rowIndex];
    row.amount = row.unpaidAmount || 0;
    if (row.id !== undefined && row.id !== null) {
      const key = String(row.id);
      if (!selectedRowKeys.value.some((item) => String(item) === key)) {
        selectedRowKeys.value = [...selectedRowKeys.value, row.id];
      }
    }
  };

  const getItemsList = () => {
    return renderData.value;
  };
  watch(supplierId, (newVal, oldVal) => {
    query();
  });

  const clearAll = () => {
    renderData.value = [];
    selectedRowKeys.value = [];
  };

  defineExpose({ query, getItemsList, clearAll });
</script>

<style lang="less" scoped>
  .purchase-order-table {
    width: 100%;
    min-height: 140px;
  }
</style>
