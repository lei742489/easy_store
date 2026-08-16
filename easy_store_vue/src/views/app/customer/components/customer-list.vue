<template>
  <div class="csst">
    <a-row>
      <a-col :flex="1">
        <a-form
          :model="formModel"
          :label-col-props="{ span: 6 }"
          :wrapper-col-props="{ span: 18 }"
          label-align="left"
          auto-label-width
        >
          <a-row :gutter="24">
            <a-col :span="14">
              <a-form-item field="name" label="客户名称:">
                <SearchModal
                  v-model:customer-name="formModel.name"
                  placeholder="客户名称 / 联系人 / 电话"
                ></SearchModal>
              </a-form-item>
            </a-col>
            <a-col :span="10">
              <a-form-item field="status" label="状态">
                <a-select v-model="formModel.status" placeholder="请选择 ...">
                  <a-option :value="1">启用</a-option>
                  <a-option :value="0">禁用</a-option>
                </a-select>
              </a-form-item>
            </a-col>

            <!--              <a-col :span="8">
              <a-form-item field="contentType" label="联系人">
                <a-select
                  v-model="formModel.contactName"
                  :options="orderTypeOptions"
                  placeholder="请选择"
                />
              </a-form-item>
            </a-col>-->
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
      id="a-table"
      row-key="id"
      :loading="loading"
      :pagination="pagination"
      :columns="(columns as TableColumnData[])"
      :data="renderData"
      :bordered="true"
      :scrollbar="true"
      :scroll="{ x: '100%', y: 540 }"
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
      <template #def="{ record }">
        <span>{{ record.categoryId_dictText || '全部分类' }}</span>
      </template>

      <template #operations="{ record }">
        <a-button type="text" size="small" @click="handelEdit(record)">
          编辑</a-button
        >
        <a-divider direction="vertical" />
        <a-popconfirm :content="`确认删除该条数据?`" @ok="handelRemove(record)">
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
        <a-divider direction="vertical" />
        <a-button
          type="text"
          size="small"
          @click="handelReceivePayment(record)"
        >
          收款</a-button
        >
      </template>
    </a-table>
    <customer-modal ref="modalRef" @ok="search(pagination)"></customer-modal>
    <receive-payment-form-modal
      ref="receivePaymentFormModalRef"
      @ok="search(pagination)"
    ></receive-payment-form-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import { getPriceStrByH } from '@/api/common';
  import ReceivePaymentFormModal from '@/views/app/AppReceivePaymentVoucher/components/modal.vue';
  import type { AppReceivePaymentVoucher } from '@/views/app/AppReceivePaymentVoucher/types/AppReceivePaymentVoucher';
  import CustomerModal from './customer-modal.vue';
  import SearchModal from './customer-search-modal.vue';

  import {
    listPage,
    remove,
    exportXlsFile,
    importExcel,
  } from '../api/api-customer';
  import type { Customer } from '../types/customer';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof CustomerModal> | null>(null);
  const receivePaymentFormModalRef = ref<InstanceType<
    typeof ReceivePaymentFormModal
  > | null>(null);
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

  const generateFormModel = (): Customer => {
    return {
      id: undefined,
      name: '',
      contactName: '',
      mobile: '',
      phone: '',
      mail: '',
      postal: '',
      birthday: '',
      address: '',
      qq: '',
      note: '',
      categoryId: undefined,
      levelId: undefined,
      discount: undefined,
      createTime: undefined,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<Customer[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 50,
    column: 'createTime',
    order: 'desc',
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
      title: '客户名称',
      dataIndex: 'name',
      align: 'center',
    },
    {
      title: '手机',
      dataIndex: 'mobile',
      align: 'center',
    },
    {
      title: '分类',
      dataIndex: 'categoryId_dictText',
      align: 'center',
      slotName: 'def',
    },
    {
      title: '等级',
      dataIndex: 'levelId_dictText',
      align: 'center',
    },

    {
      title: '应收款',
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
      title: '状态',
      dataIndex: 'status',
      align: 'center',
      slotName: 'status',
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      ellipsis: true,
    },

    /* {
      title: '建立时间',
      dataIndex: 'createTime',
      align: 'center',
    }, */
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 300,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 50,
      column: 'createTime',
      order: 'desc',
    }
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

  const queryByCategoryId = (categoryId: number) => {
    if (categoryId !== 0) {
      formModel.value.categoryId = categoryId;
    } else {
      formModel.value.categoryId = undefined;
    }

    search();
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

  const handelRemove = async (data: Customer) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const handelEdit = (item: Customer) => {
    modalRef.value?.showModal(item);
  };

  const handelReceivePayment = (record: Customer) => {
    const item: AppReceivePaymentVoucher = {
      customerId: record.id?.toString(),
      customerId_dictText: record.name,
    };
    receivePaymentFormModalRef.value?.showModal(item);
  };

  const dbRowClick = (record: Customer, rowIndex: number) => {
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

  defineExpose({ queryByCategoryId });
</script>

<style lang="less" scoped>
  .csst {
    min-height: 400px;
  }
</style>
