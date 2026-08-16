<template>
  <div class="settler-item-table">
    <a-table
      id="a-table"
      row-key="id"
      :loading="loading"
      :pagination="false"
      :columns="(columns as TableColumnData[])"
      :data="renderData"
      :bordered="{ cell: true }"
      :scrollbar="true"
      :summary="true"
      size="small"
      :scroll="{ x: '100%', y: 130 }"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>
      <template #settleId="{ rowIndex }">
        <account-settle-select
          v-model:settle-id="renderData[rowIndex].settleId"
          v-model:settle-text="renderData[rowIndex].settleId_dictText"
        ></account-settle-select>
      </template>
      <template #amount="{ rowIndex }">
        <a-input-number
          v-model="renderData[rowIndex].amount"
          :precision="2"
          :min="0"
        ></a-input-number>
      </template>
      <template #note="{ rowIndex }">
        <a-input v-model="renderData[rowIndex].note"></a-input>
      </template>
      <template #summary-cell="{ column, record }">
        <div v-if="column.dataIndex == 'settleId'">
          <div style="width: 100%; text-align: right">合计:</div>
        </div>

        <div v-if="column.dataIndex === 'amount'"
          >￥{{ formatPrice(record[column.dataIndex]) }}</div
        >
      </template>

      <template #operations="{ rowIndex }">
        <a-popconfirm
          :content="`确认删除该条数据?`"
          @ok="handelRemove(rowIndex)"
        >
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
  import useLoading from '@/hooks/loading';
  import { computed, ref } from 'vue';
  import { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { AppReceivePaymentSettleItem } from '@/views/app/AppReceivePaymentVoucher/types/AppReceivePaymentSettleItem';
  import AccountSettleSelect from '@/views/app/AppAccountSettle/components/SelectModal.vue';
  import { formatPrice } from '@/api/common';
  import { Message } from '@arco-design/web-vue';
  import { listByPaymentId } from '../api/api-AppReceivePaymentSettleItem';

  const { loading, setLoading } = useLoading(false);

  const renderData = ref<AppReceivePaymentSettleItem[]>([]);
  const totalAmount = defineModel<number>('totalAmount');

  const defaultItem: AppReceivePaymentSettleItem = {
    id: undefined,
    settleId: undefined,
    settleId_dictText: undefined,
    amount: undefined,
    note: undefined,
  };

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 80,
    },
    {
      title: '帐户名称',
      dataIndex: 'settleId',
      align: 'center',
      slotName: 'settleId',
    },
    {
      title: '金额',
      dataIndex: 'amount',
      align: 'center',
      width: 200,
      slotName: 'amount',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      slotName: 'note',
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 120,
    },
  ]);

  const initData = (size = 0) => {
    const max = 8 - size;
    for (let i = 0; i < max; i += 1) {
      const item: AppReceivePaymentSettleItem = {};
      Object.assign(item, defaultItem);
      renderData.value.push(item);
    }
  };
  const fetchData = async (paymentId?: string) => {
    setLoading(true);
    try {
      const { data } = await listByPaymentId(paymentId);
      renderData.value = data!;
      initData(data.length);
    } finally {
      setLoading(false);
    }
  };

  const getItemsList = () => {
    const result: AppReceivePaymentSettleItem[] = [];
    renderData.value.forEach((item) => {
      if (item.settleId || item.amount) {
        if (
          (item.settleId && !item.amount) ||
          (!item.settleId && item.amount)
        ) {
          const errorTxt = '付款账户录入错误';
          Message.error(errorTxt);
          throw Error(errorTxt);
        }
        result.push(item);
      }
    });
    return result;
  };

  const handelRemove = (idx: number) => {
    renderData.value.splice(idx, 1);
  };

  const clearAll = () => {
    renderData.value = [];
    initData();
  };

  defineExpose({ getItemsList, fetchData, initData, clearAll });
</script>

<style lang="less" scoped>
  .settler-item-table {
    width: 100%;
    min-height: 120px;
  }
</style>
