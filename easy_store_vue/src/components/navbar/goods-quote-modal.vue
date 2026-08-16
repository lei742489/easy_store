<template>
  <a-modal
    width="92%"
    :visible="visible"
    :footer="false"
    unmount-on-close
    @cancel="handleCancel"
  >
    <template #title>货品报价</template>
    <a-table
      row-key="id"
      :loading="loading"
      :pagination="pagination"
      :columns="(columns as TableColumnData[])"
      :data="renderData"
      :bordered="{ cell: true }"
      :scrollbar="true"
      :scroll="{ x: '100%', y: 560 }"
      size="small"
      @page-change="onPageChange"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
      </template>
      <template #stock="{ record }">
        <span :class="{ 'negative-stock': Number(record.stock || 0) < 0 }">
          {{ formatPrice(record.stock || 0) }}
        </span>
      </template>
      <template #purPrc="{ record }">
        {{ formatAmount(record.purPrc) }}
      </template>
      <template #tradePrc="{ record }">
        {{ formatAmount(record.tradePrc) }}
      </template>
      <template #salePrc="{ record }">
        {{ formatAmount(record.salePrc) }}
      </template>
      <template #costPrc="{ record }">
        {{ formatAmount(record.costPrice) }}
      </template>
      <template #operations="{ record }">
        <a-button type="text" size="small" @click="handleEdit(record)">
          修改
        </a-button>
      </template>
    </a-table>
  </a-modal>
  <form-modal ref="goodsModalRef" @ok="fetchData(pagination.current)" />
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { formatPrice } from '@/api/common';
  import { getUserInfo } from '@/api/user';
  import type { Pagination } from '@/types/global';
  import type { PolicyParams } from '@/api/list';
  import { useUserStore } from '@/store';
  import { listQuotePage } from '@/views/app/goods/api/api-AppGoods';
  import FormModal from '@/views/app/goods/components/goods-modal.vue';
  import type { AppGoods } from '@/views/app/goods/types/AppGoods';

  const visible = ref(false);
  const loading = ref(false);
  const userStore = useUserStore();
  const searchKey = ref('');
  const renderData = ref<AppGoods[]>([]);
  const goodsModalRef = ref<InstanceType<typeof FormModal> | null>(null);

  const pagination = reactive<Pagination>({
    current: 1,
    pageSize: 20,
    total: 0,
    showTotal: true,
  });

  const hasDataViewPermission = (permissionCode: string) =>
    userStore.isRoot === 1 ||
    Boolean(userStore.permissionCodes?.includes(permissionCode));

  const columns = computed<TableColumnData[]>(() => {
    const data: TableColumnData[] = [
      {
        title: '行号',
        dataIndex: 'index',
        slotName: 'index',
        align: 'center',
        width: 80,
      },
      {
        title: '品名规格',
        dataIndex: 'title',
        align: 'left',
        width: 520,
        ellipsis: true,
        tooltip: true,
      },
      {
        title: '库存',
        dataIndex: 'stock',
        slotName: 'stock',
        align: 'right',
        width: 100,
      },
      {
        title: '单位',
        dataIndex: 'unit',
        align: 'center',
        width: 80,
      },
    ];
    if (hasDataViewPermission('data_view:purchase_price')) {
      data.push({
        title: '进货价',
        dataIndex: 'purPrc',
        slotName: 'purPrc',
        align: 'right',
        width: 120,
      });
    }
    if (hasDataViewPermission('data_view:trade_price')) {
      data.push({
        title: '批发价',
        dataIndex: 'tradePrc',
        slotName: 'tradePrc',
        align: 'right',
        width: 120,
      });
    }
    if (hasDataViewPermission('data_view:sale_price')) {
      data.push({
        title: '零售价',
        dataIndex: 'salePrc',
        slotName: 'salePrc',
        align: 'right',
        width: 120,
      });
    }
    if (hasDataViewPermission('data_view:cost_price')) {
      data.push({
        title: '成本价',
        dataIndex: 'costPrice',
        slotName: 'costPrc',
        align: 'right',
        width: 120,
      });
    }
    data.push({
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 90,
    });
    return data;
  });

  const fetchData = async (current = 1) => {
    loading.value = true;
    try {
      const params = {
        current,
        pageNo: current,
        pageSize: pagination.pageSize,
        title: searchKey.value,
      } as unknown as PolicyParams;
      const { data } = await listQuotePage(params);
      renderData.value = data.records || [];
      pagination.total = data.total || 0;
      pagination.current = data.current || current;
    } finally {
      loading.value = false;
    }
  };

  const refreshDataViewPermissions = async () => {
    const { data } = await getUserInfo();
    userStore.setInfo({
      isRoot: data.isRoot,
      permissionCodes: data.permissionCodes || [],
    });
  };

  const showModal = async (key = '') => {
    searchKey.value = key.trim();
    visible.value = true;
    try {
      await refreshDataViewPermissions();
    } finally {
      fetchData(1);
    }
  };

  const handleCancel = () => {
    visible.value = false;
  };

  const onPageChange = (current: number) => {
    fetchData(current);
  };

  const handleEdit = (record: AppGoods) => {
    goodsModalRef.value?.showModal(record);
  };

  const formatAmount = (value?: number) => `￥${formatPrice(value || 0)}`;

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .negative-stock {
    color: #f53f3f;
  }
</style>
