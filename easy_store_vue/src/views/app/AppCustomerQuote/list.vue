<template>
  <div class="container">
    <a-card
      class="general-card"
      :title="$t('menu.custom.customerQuotation')"
      style="margin-top: 12px"
    >
      <a-row>
        <a-col :flex="1">
          <a-form
            :model="formModel"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            label-align="right"
          >
            <a-row :gutter="24">
              <a-col :span="12">
                <a-form-item field="customerKey" label="客户">
                  <a-auto-complete
                    v-model="formModel.customerKey"
                    :data="customerSearchData"
                    :allow-clear="true"
                    placeholder="客户名称 / 拼音首字母"
                    :filter-option="() => true"
                    @search="handleCustomerSearch"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item field="goodsKey" label="商品">
                  <a-auto-complete
                    v-model="formModel.goodsKey"
                    :data="goodsSearchData"
                    :allow-clear="true"
                    placeholder="商品名称 / 拼音首字母"
                    :filter-option="() => true"
                    @search="handleGoodsSearch"
                  >
                    <template #option="{ data }">
                      <span>{{ data.label || data.value }}</span>
                    </template>
                  </a-auto-complete>
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
          <a-button type="primary" @click="handleEdit({})">
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
        id="a-table"
        row-key="id"
        :loading="loading"
        :pagination="pagination"
        :columns="(columns as TableColumnData[])"
        :data="renderData"
        :bordered="{ cell: true }"
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
        <template #salePrc="{ record }">
          {{ formatAmount(record.salePrc) }}
        </template>
        <template #tradePrc="{ record }">
          {{ formatAmount(record.tradePrc) }}
        </template>
        <template #quotePrice="{ record }">
          <span class="quote-price">{{ formatAmount(record.quotePrice) }}</span>
        </template>
        <template #operations="{ record }">
          <a-button type="text" size="small" @click="handleEdit(record)">
            编辑
          </a-button>
          <a-divider style="margin: 0" direction="vertical" />
          <a-popconfirm content="确认删除该条报价?" @ok="handleRemove(record)">
            <a-button type="text" size="small">删除</a-button>
          </a-popconfirm>
        </template>
      </a-table>
    </a-card>

    <form-modal ref="modalRef" @ok="search(pagination)" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Pagination } from '@/types/global';
  import { PolicyParams } from '@/api/list';
  import { formatPrice } from '@/api/common';
  import useLoading from '@/hooks/loading';
  import type { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import { searchKey as searchCustomerKey } from '@/views/app/customer/api/api-customer';
  import { searchKey as searchGoodsKey } from '@/views/app/goods/api/api-AppGoods';
  import FormModal from './components/modal.vue';
  import type { AppCustomerQuote } from './types/AppCustomerQuote';
  import { listPage, remove } from './api/api-AppCustomerQuote';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';

  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppCustomerQuote[]>([]);
  const customerSearchData = ref<string[]>([]);
  const goodsSearchData = ref<GoodsSearchResult[]>([]);

  const densityList = computed(() => [
    { name: '迷你', value: 'mini' },
    { name: '偏小', value: 'small' },
    { name: '中等', value: 'medium' },
    { name: '偏大', value: 'large' },
  ]);

  const generateFormModel = (): AppCustomerQuote => ({
    customerKey: undefined,
    goodsKey: undefined,
  });

  const formModel = ref(generateFormModel());

  const basePagination: Pagination = {
    current: 1,
    pageSize: 50,
    total: 0,
    order: 'desc',
    column: 'id',
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
      width: 80,
    },
    {
      title: '客户名称',
      dataIndex: 'customerName',
      align: 'center',
      width: 190,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '商品名称',
      dataIndex: 'goodsTitle',
      align: 'center',
      width: 260,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '单位',
      dataIndex: 'unit',
      align: 'center',
      width: 90,
    },

    {
      title: '大客户价',
      dataIndex: 'quotePrice',
      slotName: 'quotePrice',
      align: 'center',
      width: 130,
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: 'center',
      width: 180,
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 160,
    },
  ]);

  const fetchData = async (
    params: PolicyParams = {
      current: 1,
      pageSize: 50,
      order: 'desc',
      column: 'id',
    }
  ) => {
    setLoading(true);
    try {
      const { data } = await listPage(params);
      renderData.value = data.records || [];
      pagination.total = data.total;
      pagination.current = data.current || 1;

      nextTick(() => {
        const tableEl = document.getElementById('a-table');
        const body = tableEl?.querySelector('.arco-table-body');
        if (body) {
          body.scrollTop = 0;
        }
      });
    } finally {
      setLoading(false);
    }
  };

  const search = (p?: Pagination) => {
    const pg: Pagination = p || { ...basePagination, current: 1 };
    fetchData({
      ...pg,
      ...formModel.value,
    } as unknown as PolicyParams);
  };

  const reset = () => {
    formModel.value = generateFormModel();
    search();
  };

  const onPageChange = (current: number) => {
    fetchData({
      ...pagination,
      current,
      ...formModel.value,
    } as unknown as PolicyParams);
  };

  const onSorterChange = (column: string, order: string) => {
    basePagination.column = column;
    basePagination.order = order.replace('end', '');
    search();
  };

  const handleSelectDensity = (
    val: string | number | Record<string, any> | undefined
  ) => {
    size.value = val as SizeProps;
  };

  const handleRemove = async (record: AppCustomerQuote) => {
    await remove(record);
    Message.success('操作成功');
    search(pagination);
  };

  const handleEdit = (item: AppCustomerQuote) => {
    modalRef.value?.showModal(item);
  };

  const dbRowClick = (record: any) => {
    handleEdit(record);
  };

  const handleCustomerSearch = async (key: string) => {
    if (!key) {
      customerSearchData.value = [];
      return;
    }
    const { data } = await searchCustomerKey(key);
    customerSearchData.value = data || [];
  };

  const handleGoodsSearch = async (key: string) => {
    if (!key) {
      goodsSearchData.value = [];
      return;
    }
    const { data } = await searchGoodsKey(key, 1);
    goodsSearchData.value = data || [];
  };

  const formatAmount = (value?: number) =>
    value == null ? '' : `￥${formatPrice(value)}`;

  fetchData();
</script>

<style lang="less" scoped>
  .container {
    padding: 0 20px 20px 20px;
  }

  .quote-price {
    color: #d94841;
    font-weight: 600;
  }
</style>
