<template>
  <div class="container">
    <a-card class="general-card" title="操作日志">
      <a-form :model="form" auto-label-width>
        <a-row :gutter="20">
          <a-col :span="6">
            <a-form-item field="operatorName" label="操作员">
              <a-input
                v-model="form.operatorName"
                placeholder="请输入操作员名称"
                allow-clear
                @press-enter="search"
              />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item field="operationType" label="操作类型">
              <a-select
                v-model="form.operationType"
                placeholder="全部类型"
                allow-clear
              >
                <a-option value="add">添加</a-option>
                <a-option value="edit">编辑</a-option>
                <a-option value="remove">删除</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item field="dates" label="日期">
              <a-range-picker
                v-model="form.dates"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="2" class="search-actions">
            <a-space>
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
      </a-form>

      <a-divider />

      <a-table
        row-key="id"
        :loading="loading"
        :data="records"
        :columns="columns"
        :pagination="pagination"
        :bordered="true"
        :scroll="{ x: 1250, y: 560 }"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #operationType="{ record }">
          <span :class="`operation-${record.operationType}`">
            {{ operationTypeText(record.operationType) }}
          </span>
        </template>
        <template #dataJson="{ record }">
          <a-button type="text" size="small" @click="showData(record.dataJson)">
            查看数据
          </a-button>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:visible="jsonVisible"
      title="操作数据"
      width="760px"
      :footer="false"
    >
      <pre class="json-content">{{ selectedJson }}</pre>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import dayjs from 'dayjs';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { useUserStore } from '@/store';
  import useLoading from '@/hooks/loading';
  import {
    listPage,
    OperationLogRecord,
  } from './api';

  const userStore = useUserStore();
  const { loading, setLoading } = useLoading(false);
  const records = ref<OperationLogRecord[]>([]);
  const jsonVisible = ref(false);
  const selectedJson = ref('');
  const form = reactive({
    operatorName: '',
    operationType: undefined as string | undefined,
    clientIp: '',
    dates: [
      dayjs().startOf('month').format('YYYY-MM-DD'),
      dayjs().endOf('month').format('YYYY-MM-DD'),
    ] as string[],
  });
  const pagination = reactive({
    current: 1,
    pageSize: 50,
    total: 0,
    showTotal: true,
    showPageSize: true,
  });

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      width: 80,
      align: 'center',
    },
    {
      title: '操作员',
      dataIndex: 'operatorName',
      width: 130,
      align: 'center',
    },
    {
      title: '菜单名称',
      dataIndex: 'menuName',
      width: 150,
      align: 'center',
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '操作类型',
      dataIndex: 'operationType',
      slotName: 'operationType',
      width: 100,
      align: 'center',
    },
    {
      title: 'IP',
      dataIndex: 'clientIp',
      width: 150,
      align: 'center',
    },
    {
      title: '操作日期',
      dataIndex: 'operateTime',
      width: 180,
      align: 'center',
    },
    {
      title: '操作数据',
      dataIndex: 'dataJson',
      slotName: 'dataJson',
      width: 120,
      align: 'center',
    },
  ]);

  const operationTypeText = (type?: string) => {
    if (type === 'add') return '添加';
    if (type === 'edit') return '编辑';
    if (type === 'remove') return '删除';
    return type || '-';
  };

  const fetchData = async () => {
    if (userStore.isRoot !== 1) return;
    setLoading(true);
    try {
      const { data } = await listPage({
        operatorName: form.operatorName || undefined,
        operationType: form.operationType,
        clientIp: form.clientIp || undefined,
        startDate: form.dates?.[0],
        endDate: form.dates?.[1],
        current: pagination.current,
        pageSize: pagination.pageSize,
      });
      records.value = data?.records || [];
      pagination.total = data?.total || 0;
      pagination.current = data?.current || 1;
    } finally {
      setLoading(false);
    }
  };

  const search = () => {
    pagination.current = 1;
    fetchData();
  };

  const reset = () => {
    form.operatorName = '';
    form.operationType = undefined;
    form.clientIp = '';
    form.dates = [
      dayjs().startOf('month').format('YYYY-MM-DD'),
      dayjs().endOf('month').format('YYYY-MM-DD'),
    ];
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

  const showData = (data?: string) => {
    if (!data) {
      selectedJson.value = '{}';
    } else {
      try {
        selectedJson.value = JSON.stringify(JSON.parse(data), null, 2);
      } catch {
        selectedJson.value = data;
      }
    }
    jsonVisible.value = true;
  };

  onMounted(fetchData);
</script>

<style lang="less" scoped>
  .container {
    padding: 16px 20px;
  }

  .search-actions {
    display: flex;
    align-items: flex-end;
    padding-bottom: 4px;
  }

  .operation-add {
    color: rgb(var(--green-6));
  }

  .operation-edit {
    color: rgb(var(--arcoblue-6));
  }

  .operation-remove {
    color: rgb(var(--red-6));
  }

  .json-content {
    max-height: 560px;
    margin: 0;
    overflow: auto;
    padding: 12px;
    color: var(--color-text-1);
    background: var(--color-fill-2);
    white-space: pre-wrap;
    word-break: break-all;
  }
</style>
