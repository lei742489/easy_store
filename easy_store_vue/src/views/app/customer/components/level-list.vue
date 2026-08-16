<template>
  <a-modal
    :visible="visible"
    title="客户等级"
    :width="800"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <div class="container">
      <a-card class="general-card">
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
              <div class="action-icon" @click="search"
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
          :bordered="false"
          :size="size"
          @page-change="onPageChange"
          @row-dblclick="dbRowClick"
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
            <a-divider direction="vertical" />
            <a-popconfirm
              :content="`确认删除该条数据?`"
              @ok="handelRemove(record)"
            >
              <a-button type="text" size="small">删除</a-button>
            </a-popconfirm>
          </template>
        </a-table>
      </a-card>

      <form-modal ref="modalRef" @ok="search()"></form-modal>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import type { categoryType } from '@/views/app/customer/types/category';
  import FormModal from './level-modal.vue';

  import { listPage, remove } from '../api/api-AppCustomerLevel';
  import type { AppCustomerLevel } from '../types/AppCustomerLevel';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const visible = ref(false);

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

  const generateFormModel = (): AppCustomerLevel => {
    return {
      id: undefined,
      title: undefined,
      createTime: undefined,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppCustomerLevel[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 20,
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
      dataIndex: 'title',
      align: 'center',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: 'center',
    },

    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
    },
  ]);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const fetchData = async (
    params: PolicyParams = { current: 1, pageSize: 20 }
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

  const search = () => {
    fetchData({
      ...basePagination,
      ...formModel.value,
    } as unknown as PolicyParams);
  };

  const onPageChange = (current: number) => {
    fetchData({ ...basePagination, current });
  };

  const showModal = () => {
    visible.value = true;
    fetchData();
  };

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

  const handelRemove = async (data: AppCustomerLevel) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const handelEdit = (item: AppCustomerLevel) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: AppCustomerLevel, rowIndex: number) => {
    handelEdit(record);
  };

  const handleCancel = async () => {
    visible.value = false;
    formModel.value = generateFormModel();
  };

  const handleOk = async () => {
    emit('ok', 1);
    handleCancel();
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .container {
  }
</style>
