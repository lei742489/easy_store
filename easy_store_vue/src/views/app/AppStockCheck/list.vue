<template>
  <div class="container">
    <a-card class="general-card" title="盘点单列表" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="formModel"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="right"
            auto-label-width
            style="margin-top: 10px"
          >
            <a-row :gutter="24">
              <a-col :span="12">
                <a-form-item field="searchKey" label="单号查询">
                  <a-input
                    v-model="formModel.searchKey"
                    placeholder="单据编号或说明"
                    allow-clear
                    @press-enter="search"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item field="createTime" label="日期">
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
              查询
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
          <a-button type="primary" @click="handleEdit({})">
            <template #icon><icon-plus /></template>
            新增
          </a-button>
        </a-col>
        <a-col class="table-actions" :span="12">
          <a-tooltip content="刷新">
            <a-button type="text" @click="search">
              <template #icon><icon-refresh /></template>
            </a-button>
          </a-tooltip>
        </a-col>
      </a-row>

      <a-table
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        :columns="columns"
        :data="records"
        :bordered="{ cell: true }"
        :scroll="{ x: 860, y: 540 }"
        @page-change="onPageChange"
        @row-dblclick="handleRowDoubleClick"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #operations="{ record }">
          <a-button type="text" size="small" @click="handleEdit(record)">
            编辑
          </a-button>
          <a-divider style="margin: 0" direction="vertical" />
          <a-popconfirm content="确认删除该盘点单？" @ok="handleRemove(record)">
            <a-button type="text" size="small">删除</a-button>
          </a-popconfirm>
        </template>
      </a-table>
    </a-card>
    <form-modal ref="modalRef" @ok="search()" />
  </div>
</template>

<script lang="ts" setup>
  import { h, reactive, ref } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import { formatPrice } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import useLoading from '@/hooks/loading';
  import type { Pagination } from '@/types/global';
  import { listPage, remove } from './api/api-AppStockCheck';
  import FormModal from './components/modal.vue';
  import type { AppStockCheck } from './types/AppStockCheck';

  const { loading, setLoading } = useLoading(false);
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const timeSelectRef = ref<InstanceType<typeof TimeSelect> | null>(null);
  const records = ref<AppStockCheck[]>([]);

  const createForm = (): AppStockCheck => ({
    searchKey: undefined,
    createTime_begin: undefined,
    createTime_end: undefined,
  });
  const formModel = reactive<AppStockCheck>(createForm());
  const pagination = reactive<Pagination>({
    current: 1,
    pageSize: 50,
    order: 'desc',
    column: 'createTime',
    showTotal: true,
  });

  const amountCell =
    (field: 'profitLossAmount' | 'profitLossQuantity') => (record: any) => {
      const value = Number(record.record[field] || 0);
      const text =
        field === 'profitLossAmount'
          ? `¥${formatPrice(value)}`
          : value.toFixed(2);
      return h(
        'span',
        { class: value < 0 ? 'negative-value' : 'positive-value' },
        text
      );
    };

  const columns: TableColumnData[] = [
    {
      title: '行号',
      dataIndex: 'index',
      slotName: 'index',
      width: 76,
      align: 'center',
    },
    {
      title: '单据日期',
      dataIndex: 'createTime',
      minWidth: 160,
      align: 'center',
      render: (record: any) =>
        h('span', null, String(record.record.createTime || '').split(' ')[0]),
    },
    {
      title: '单据编号',
      dataIndex: 'orderNo',
      minWidth: 200,
      align: 'center',
    },
    {
      title: '盈亏数量',
      dataIndex: 'profitLossQuantity',
      minWidth: 160,
      align: 'right',
      render: amountCell('profitLossQuantity'),
    },
    {
      title: '盈亏金额',
      dataIndex: 'profitLossAmount',
      minWidth: 180,
      align: 'right',
      render: amountCell('profitLossAmount'),
    },

    {
      title: '营业员',
      dataIndex: 'cashierName',
      minWidth: 130,
      align: 'center',
    },
    {
      title: '备注说明',
      dataIndex: 'note',
      minWidth: 130,
      align: 'center',
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 200,
      align: 'center',
    },
  ];

  const fetchData = async (current = pagination.current) => {
    setLoading(true);
    try {
      const { data } = await listPage({
        ...formModel,
        current,
        pageSize: pagination.pageSize,
        order: 'desc',
        column: 'createTime',
      });
      records.value = (data.records || []) as AppStockCheck[];
      pagination.current = data.current || 1;
      pagination.total = data.total || 0;
    } finally {
      setLoading(false);
    }
  };

  const search = () => {
    pagination.current = 1;
    fetchData(1);
  };

  const reset = () => {
    Object.assign(formModel, createForm());
    timeSelectRef.value?.setPreset(2);
    search();
  };

  const timeSelectChange = (dates: string[]) => {
    [formModel.createTime_begin, formModel.createTime_end] = dates;
    search();
  };

  const onPageChange = (current: number) => {
    pagination.current = current;
    fetchData(current);
  };

  const handleEdit = (record: AppStockCheck) => {
    modalRef.value?.showModal(record);
  };

  const handleRowDoubleClick = (record: AppStockCheck) => {
    handleEdit(record);
  };

  const handleRemove = async (record: AppStockCheck) => {
    await remove(record);
    Message.success('操作成功');
    search();
  };

  fetchData();
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .table-actions {
    display: flex;
    justify-content: flex-end;
  }

  :deep(.positive-value) {
    color: rgb(var(--green-6));
  }

  :deep(.negative-value) {
    color: rgb(var(--red-6));
  }
</style>
