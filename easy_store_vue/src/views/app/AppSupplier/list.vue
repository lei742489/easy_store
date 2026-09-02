<template>
  <div class="container">
    <a-card class="general-card" title="供应商管理" style="margin-top: 12px">
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="formModel"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 12 }"
            :label-width="10"
            label-align="center"
          >
            <a-row :gutter="24">
              <a-col :span="8">
                <a-form-item field="status" label="状态">
                  <a-select v-model="formModel.status" placeholder="请选择 ...">
                    <a-option :value="1">启用</a-option>
                    <a-option :value="0">禁用</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="16">
                <a-form-item field="status" label="供应商">
                  <SearchModal
                    v-model:supplier-name="formModel.name"
                    placeholder="供应商名称 / 联系人 / 电话"
                  ></SearchModal>
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
            <a-upload :custom-request="uploadExcel" :show-file-list="false">
              <template #upload-button>
                <a-button :loading="uploadLoading">
                  <template #icon> <icon-upload /> </template>导入
                </a-button>
              </template>
            </a-upload>

            <a-button @click="exportXlsFile(formModel)">
              <template #icon>
                <icon-download />
              </template>
              导出
            </a-button>
            <!--
            <a-button
              :loading="payableRefreshLoading"
              @click="refreshPayableData"
            >
              <template #icon>
                <icon-refresh />
              </template>
              刷新付款金额
            </a-button>
            -->
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
        :bordered="true"
        :size="size"
        @page-change="onPageChange"
        @row-dblclick="dbRowClick"
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

        <template #price="{ record }">
          <span>￥{{ formatPrice(record.payable) }}</span>
        </template>

        <template #operations="{ record }">
          <a-button type="text" size="small" @click="handelEdit(record)">
            编辑</a-button
          >
          <a-divider direction="vertical" />
          <a-popconfirm
            :content="`确认删除该条数据?`"
            @ok="handelRemove(record)"
          >
            <a-button type="text" size="small">删除</a-button>
          </a-popconfirm>
          <a-divider direction="vertical" />
          <a-button type="text" size="small" @click="handelPayment(record)">
            付款</a-button
          >
        </template>
      </a-table>
    </a-card>
    <form-modal ref="modalRef" @ok="search(pagination)"></form-modal>
    <payment-form-modal
      ref="paymentFormModalRef"
      @ok="search(pagination)"
    ></payment-form-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import { formatPrice, getPriceStrByH } from '@/api/common';
  import PaymentFormModal from '@/views/app/AppPaymentVoucher/components/modal.vue';
  import { AppPaymentVoucher } from '@/views/app/AppPaymentVoucher/types/AppPaymentVoucher';
  import FormModal from './components/modal.vue';
  import SearchModal from './components/SupplierSearchModal.vue';

  import {
    listPage,
    remove,
    exportXlsFile,
    refreshPayable,
    importExcel,
    searchKey,
  } from './api/api-AppSupplier';
  import type { AppSupplier } from './types/AppSupplier';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const paymentFormModalRef = ref<InstanceType<typeof PaymentFormModal> | null>(
    null
  );
  const uploadLoading = ref<boolean>(false);
  const payableRefreshLoading = ref<boolean>(false);

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

  const generateFormModel = (): AppSupplier => {
    return {
      id: undefined,
      name: undefined,
      contactName: undefined,
      mobile: undefined,
      phone: undefined,
      mail: undefined,
      postal: undefined,
      address: undefined,
      qq: undefined,
      note: undefined,
      status: undefined,
      defPayable: undefined,
      payable: undefined,
      createTime: undefined,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppSupplier[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 10,
    order: undefined,
    column: undefined,
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
      title: '供应商名称',
      dataIndex: 'name',
      align: 'center',
    },
    {
      title: '联系人',
      dataIndex: 'contactName',
      align: 'center',
    },
    {
      title: '手机',
      dataIndex: 'mobile',
      align: 'center',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
    },
    {
      title: '状态',
      dataIndex: 'status',
      align: 'center',

      slotName: 'status',
    },
    {
      title: '当前应付款',
      dataIndex: 'payable',
      align: 'center',
      slotName: 'price',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
      render: (record) => {
        const data = record.record;
        return getPriceStrByH((data as any).payable);
      },
    },

    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 300,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = { current: 1, pageSize: 20 }
  ) => {
    setLoading(true);
    try {
      const { data } = await listPage(params);
      renderData.value = data.records!;
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
    search({ ...basePagination, current });
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

  const handelRemove = async (data: AppSupplier) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const refreshPayableData = async () => {
    if (payableRefreshLoading.value) return;
    payableRefreshLoading.value = true;
    try {
      const { data } = await refreshPayable();
      Message.success(`已刷新${data || 0}个供应商的应付金额`);
      await search();
    } finally {
      payableRefreshLoading.value = false;
    }
  };

  const handelEdit = (item: AppSupplier) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: AppSupplier, rowIndex: number) => {
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

  const handelPayment = (record: AppSupplier) => {
    const item: AppPaymentVoucher = {
      supplierId: record.id?.toString(),
      supplierId_dictText: record.name,
    };
    paymentFormModalRef.value?.showModal(item);
  };
</script>

<style lang="less" scoped>
  .container {
  }
</style>
