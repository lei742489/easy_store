<template>
  <div class="container">
    <a-card class="general-card" title="员工管理" style="margin-top: 12px">
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-space>
            <a-button type="primary" @click="handelEdit({})">
              <template #icon>
                <icon-plus />
              </template>
              新增
            </a-button>
          </a-space>
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
            <div class="action-icon" @click="search()"
              ><icon-refresh size="18"
            /></div>
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
        :scroll="{ y: 540 }"
        @page-change="onPageChange"
        @row-dblclick="dbRowClick"
        @sorter-change="onSorterChange"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>

        <template #operations="{ record }">
          <div class="op-actions">
            <a-button type="text" size="small" @click="handelEdit(record)">
              编辑
            </a-button>
            <a-divider style="margin: 0" direction="vertical" />
            <a-popconfirm
              :content="`确认删除该条数据?`"
              @ok="handelRemove(record)"
            >
              <a-button type="text" size="small">删除</a-button>
            </a-popconfirm>
            <a-divider style="margin: 0" direction="vertical" />
            <a-popconfirm
              :content="`确认重置该员工密码为123456?`"
              @ok="handelResetPwd(record)"
            >
              <a-button type="text" size="small" class="nowrap-btn">
                <template #icon>
                  <icon-lock />
                </template>
                重置密码
              </a-button>
            </a-popconfirm>
          </div>
        </template>
      </a-table>
    </a-card>
    <form-modal ref="modalRef" @ok="search(pagination)"></form-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import FormModal from './components/modal.vue';

  import {
    listPage,
    remove,
    resetPwd,
    exportXlsFile,
    importExcel,
  } from './api/api-AppUser';
  import type { AppUser } from './types/AppUser';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const uploadLoading = ref<boolean>(false);

  const densityList = computed(() => [
    {
      name: '迷你',
      value: 'mini',
    },
    {
      name: '偏小',
      value: 'small',
    },
    {
      name: '中等',
      value: 'medium',
    },
    {
      name: '偏大',
      value: 'large',
    },
  ]);

  const generateFormModel = (): AppUser => {
    return {
      id: undefined,
      realName: undefined,
      remarks: undefined,
      mobile: undefined,
      commissionRate: undefined,
      isRoot: 0,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppUser[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 50,
    order: 'desc',
    column: 'createTime',
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
    },
    {
      title: '名称',
      dataIndex: 'realName',
      align: 'center',
    },
    {
      title: '登录账号',
      dataIndex: 'userName',
      align: 'center',
    },
    {
      title: '角色',
      dataIndex: 'roleId_dictText',
      align: 'center',
    },
    {
      title: '手机号',
      dataIndex: 'mobile',
      align: 'center',
    },
    {
      title: '提成比例(%)',
      dataIndex: 'commissionRate',
      align: 'center',
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
      width: 250,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 50,
      order: 'desc',
      column: 'createTime',
      isRoot: 0,
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
    fetchData({ ...basePagination, current });
  };

  fetchData();
  const reset = () => {
    formModel.value = generateFormModel();
    search();
  };

  const handleSelectDensity = (
    val: string | number | Record<string, any> | undefined,
    e: Event
  ) => {
    size.value = val as SizeProps;
  };

  const handelRemove = async (data: AppUser) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const handelResetPwd = async (data: AppUser) => {
    const res: any = await resetPwd(data);
    Message.success(res.message || '操作成功');
  };

  const handelEdit = (item: AppUser) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: any, rowIndex: number) => {
    handelEdit(record);
  };

  const uploadExcel = async (e: any) => {
    if (uploadLoading.value) return;
    uploadLoading.value = true;

    try {
      const res: any = await importExcel(e.fileItem.file);
      Message.success(res.message!);
      search();
    } catch (error: any) {
      Message.error(`上传错误：${error}`);
    } finally {
      uploadLoading.value = false;
    }
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

  .op-actions {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    white-space: nowrap;
  }

  .nowrap-btn {
    white-space: nowrap;
  }
</style>
