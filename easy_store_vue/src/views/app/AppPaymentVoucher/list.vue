<template>
  <div class="container">
    <a-card class="general-card" title="付款单" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="formModel"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="center"
            auto-label-width
            style="margin-top: 10px"
          >
            <a-row :gutter="24">
              <a-col :span="7">
                <a-form-item field="supplierId" label="供应商">
                  <SearchModal
                    v-model:supplier-id="formModel.supplierId"
                    placeholder="请选择"
                  ></SearchModal>
                </a-form-item>
              </a-col>
              <a-col v-if="isRoot" :span="7">
                <a-form-item field="cashierId" label="营业员">
                  <user-select
                    v-model:user-id="formModel.cashierId"
                    placeholder="请输入营业员"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="10">
                <a-form-item field="searchKey" label="订单号">
                  <a-input
                    v-model="formModel.orderNo"
                    placeholder="请输入单号"
                  ></a-input>
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="24">
              <a-col :span="7">
                <a-form-item field="status" label="状态">
                  <a-select
                    v-model="formModel.status"
                    placeholder="全部"
                    allow-clear
                  >
                    <a-option :value="0">待审核</a-option>
                    <a-option :value="1">正常</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="13">
                <a-form-item field="createTime" label="日期">
                  <time-select
                    ref="timeSelectRef"
                    @change="timeSelectChange"
                  ></time-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-col>
        <a-divider style="height: 84px" direction="vertical" />
        <a-col :flex="'86px'" style="text-align: right">
          <a-space direction="vertical" :size="18">
            <a-button type="primary" @click="search()">
              <template #icon>
                <icon-search />
              </template>
              查询
            </a-button>
            <a-button @click="reset">
              <template #icon>
                <icon-refresh />
              </template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      <a-divider style="margin-top: 4px" />
      <a-row style="margin-bottom: 16px">
        <a-col :span="12">
          <a-space>
            <a-button type="primary" @click="handelEdit({})">
              <template #icon>
                <icon-plus />
              </template>
              新增
            </a-button>
            <a-button
              :loading="exportLoading"
              :disabled="exportLoading"
              @click="exportXls(formModel)"
            >
              <template #icon>
                <icon-download />
              </template>
              导出
            </a-button>
            <a-button
              v-if="isRoot"
              :loading="approveLoading"
              @click="handleBatchApprove"
            >
              批量审核
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
          <a-tooltip content="打印表格">
            <a-button v-print="'#a-table'" type="text"
              ><icon-printer size="18"
            /></a-button>
          </a-tooltip>
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
        id="a-table"
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        :columns="(columns as TableColumnData[])"
        :data="renderData"
        :bordered="{ cell: true }"
        :row-selection="isRoot ? rowSelection : undefined"
        :size="size"
        :scrollbar="true"
        :scroll="{ x: '100%', y: 540 }"
        @page-change="onPageChange"
        @row-dblclick="dbRowClick"
        @selection-change="handleSelectionChange"
        @sorter-change="onSorterChange"
      >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #status="{ record }">
          <span
            :style="record.status != 1 ? 'color:#eb4d4b;' : 'color:#0984e3;'"
            >{{ record.status == 1 ? '启用' : '禁用' }}</span
          >
        </template>

        <template #operations="{ record }">
          <a-button type="text" size="small" @click="handelEdit(record)">
            编辑</a-button
          >
          <a-divider style="margin: 0" direction="vertical" />
          <a-popconfirm
            :content="`确认删除该条数据?`"
            @ok="handelRemove(record)"
          >
            <a-button type="text" size="small">删除</a-button>
          </a-popconfirm>
          <template v-if="canApprove(record)">
            <a-divider style="margin: 0" direction="vertical" />
            <a-button type="text" size="small" @click="handleApprove(record)"
              >审核</a-button
            >
          </template>
        </template>
      </a-table>
    </a-card>
    <form-modal ref="modalRef" @ok="search(pagination)"></form-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, h, nextTick, reactive, ref } from 'vue';
  import { useUserStore } from '@/store';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import { getPriceStrByH } from '@/api/common';
  import TimeSelect from '@/components/menu/time-select.vue';
  import SearchModal from '@/views/app/AppSupplier/components/SupplierSelectModal.vue';
  import userSelect from '@/views/app/AppUser/components/UserSelectModel.vue';
  import FormModal from './components/modal.vue';

  import {
    listPage,
    remove,
    approve,
    exportXlsFile,
    importExcel,
  } from './api/api-AppPaymentVoucher';
  import type { AppPaymentVoucher } from './types/AppPaymentVoucher';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const uploadLoading = ref<boolean>(false);
  const approveLoading = ref<boolean>(false);
  const exportLoading = ref<boolean>(false);
  const selectedRowKeys = ref<Array<number | string>>([]);
  const rowSelection = reactive({
    type: 'checkbox',
    showCheckedAll: true,
    selectedRowKeys,
  });

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

  const generateFormModel = (): AppPaymentVoucher => {
    return {
      id: undefined,
      orderNo: undefined,
      supplierId: undefined,
      cashierId: undefined,
      cashierName: undefined,
      status: undefined,
      amount: undefined,
      note: undefined,
      searchKey: undefined,
      createTime_begin: undefined,
      createTime_end: undefined,
      selections: undefined,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppPaymentVoucher[]>([]);

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

  const statusText = (status?: number) => (status === 0 ? '待审核' : '正常');
  const statusColor = (status?: number) =>
    status === 0 ? '#eb4d4b' : '#00a870';

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '订单号',
      dataIndex: 'orderNo',
      align: 'center',
    },
    {
      title: '状态',
      dataIndex: 'status',
      align: 'center',
      render: (record) => {
        const data = record.record as AppPaymentVoucher;
        return h(
          'span',
          { style: { color: statusColor(data.status) } },
          statusText(data.status)
        );
      },
    },
    {
      title: '供应商',
      dataIndex: 'supplierId_dictText',
      align: 'center',
    },
    {
      title: '付款金额',
      dataIndex: 'amount',
      align: 'center',
      render: (record) => {
        const data = record.record;
        return getPriceStrByH((data as any).amount);
      },
    },
    {
      title: '营业员',
      dataIndex: 'cashierName',
      align: 'center',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
    },
    {
      title: '建立时间',
      dataIndex: 'createTime',
      align: 'center',
    },

    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 220,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 50,
      order: 'desc',
      column: 'createTime',
    }
  ) => {
    setLoading(true);
    try {
      const { data } = await listPage(params);
      renderData.value = data.records!;
      selectedRowKeys.value = [];
      pagination.total = data.total;
      pagination.current = data.current || 1;

      nextTick(() => {
        const tableEl = document.getElementById('a-table');
        if (tableEl) {
          const body = tableEl.querySelector('.arco-table-body');
          if (body) {
            body.scrollTop = 0;
          }
        }
      });
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

  const handelRemove = async (data: AppPaymentVoucher) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const canApprove = (record: AppPaymentVoucher) => {
    return isRoot.value && record.status === 0;
  };

  const handleSelectionChange = (keys: Array<number | string>) => {
    selectedRowKeys.value = keys;
  };

  const handleApprove = async (data: AppPaymentVoucher) => {
    if (!data.id) return;
    approveLoading.value = true;
    try {
      await approve({ id: data.id });
      Message.success('审核成功');
      search();
    } finally {
      approveLoading.value = false;
    }
  };

  const handleBatchApprove = async () => {
    const selectedSet = new Set(selectedRowKeys.value.map((id) => String(id)));
    const ids = renderData.value
      .filter(
        (item) =>
          item.id !== undefined &&
          item.status === 0 &&
          selectedSet.has(String(item.id))
      )
      .map((item) => item.id as number);
    if (ids.length === 0) {
      Message.warning('请选择待审核单据');
      return;
    }
    approveLoading.value = true;
    try {
      await approve({ ids });
      Message.success('审核成功');
      search();
    } finally {
      approveLoading.value = false;
    }
  };

  const handelEdit = (item: AppPaymentVoucher) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: AppPaymentVoucher, rowIndex: number) => {
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

  const timeSelectChange = (arr: string[]) => {
    [formModel.value.createTime_begin, formModel.value.createTime_end] = arr;
    search();
  };

  const exportXls = (data: AppPaymentVoucher) => {
    if (exportLoading.value) {
      return;
    }
    if (renderData.value.length <= 0) {
      return;
    }
    exportLoading.value = true;
    const ids = renderData.value.map((item) => item.id).join(',');
    data.selections = ids;
    try {
      exportXlsFile(data);
    } finally {
      window.setTimeout(() => {
        exportLoading.value = false;
      }, 3000);
    }
  };
</script>

<style lang="less" scoped>
  .container {
  }
</style>
