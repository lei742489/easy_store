<template>
  <div class="container">
    <a-card class="general-card" title="角色管理" style="margin-top: 12px">
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-button type="primary" @click="handelEdit({})">
            <template #icon>
              <icon-plus />
            </template>
            新增
          </a-button>
        </a-col>

        <a-col
          :span="12"
          style="
            display: flex;
            align-items: center;
            justify-content: end;
            gap: 10px;
          "
        >
          <a-tooltip content="刷新">
            <div class="action-icon" @click="search()">
              <icon-refresh size="18" />
            </div>
          </a-tooltip>
          <a-dropdown @select="handleSelectDensity">
            <a-tooltip content="密度">
              <div class="action-icon"><icon-line-height size="18" /></div>
            </a-tooltip>
            <template #content>
              <a-doption
                v-for="item in densityList"
                :key="item.value"
                :value="item.value"
                :class="{ active: item.value === size }"
              >
                <span>{{ item.name }}</span>
              </a-doption>
            </template>
          </a-dropdown>
        </a-col>
      </a-row>

      <a-table
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        :columns="(columns as TableColumnData[])"
        :data="renderData"
        :bordered="true"
        :size="size"
        :scrollbar="true"
        :scroll="{ x: '100%', y: 540 }"
        @page-change="onPageChange"
        @row-dblclick="dbRowClick"
        @sorter-change="onSorterChange"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #status="{ record }">
          <span
            :style="record.status !== 1 ? 'color:#eb4d4b;' : 'color:#0984e3;'"
          >
            {{ record.status === 1 ? '启用' : '禁用' }}
          </span>
        </template>
        <template #operations="{ record }">
          <a-button type="text" size="small" @click="handelEdit(record)">
            编辑
          </a-button>
          <a-divider style="margin: 0" direction="vertical" />
          <a-popconfirm
            :content="`确认删除该角色?`"
            @ok="handelRemove(record)"
          >
            <a-button type="text" size="small">删除</a-button>
          </a-popconfirm>
        </template>
      </a-table>
    </a-card>
    <form-modal ref="modalRef" @ok="search(pagination)" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Pagination } from '@/types/global';
  import { PolicyParams } from '@/api/list';
  import useLoading from '@/hooks/loading';
  import FormModal from './components/modal.vue';
  import { listPage, remove } from './api/api-AppRole';
  import type { AppRole } from './types/AppRole';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);

  const densityList = computed(() => [
    { name: '迷你', value: 'mini' },
    { name: '偏小', value: 'small' },
    { name: '中等', value: 'medium' },
    { name: '偏大', value: 'large' },
  ]);

  const generateFormModel = (): AppRole => ({
    id: undefined,
    name: undefined,
    remarks: undefined,
    status: undefined,
  });

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppRole[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 50,
    showTotal: true,
  };

  const pagination = reactive({
    ...basePagination,
  });

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 90,
    },
    {
      title: '角色名称',
      dataIndex: 'name',
      align: 'center',
    },
    {
      title: '状态',
      dataIndex: 'status',
      slotName: 'status',
      align: 'center',
      width: 120,
    },
    {
      title: '备注',
      dataIndex: 'remarks',
      align: 'center',
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 180,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 50,
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

  const search = (p?: Pagination) => {
    const pg: Pagination = p || basePagination;
    fetchData({
      ...pg,
      ...formModel.value,
    } as unknown as PolicyParams);
  };

  const onPageChange = (current: number) => {
    search({ ...basePagination, current });
  };

  fetchData();

  const handleSelectDensity = (
    val: string | number | Record<string, any> | undefined
  ) => {
    size.value = val as SizeProps;
  };

  const handelRemove = async (data: AppRole) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const handelEdit = (item: AppRole) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: any) => {
    handelEdit(record);
  };

  const onSorterChange = (column: string, order: string) => {
    basePagination.column = column;
    basePagination.order = order.replace('end', '');
    search();
  };
</script>

<style lang="less" scoped>
  .container {
  }
</style>
