<template>
  <div class="container">
    <div>
      <a-row>
        <a-col :span="20">
          <a-form
            :model="formModel"
            :label-col-props="{ span: 4 }"
            :wrapper-col-props="{ span: 18 }"
            :label-width="10"
            label-align="center"
            style="margin-top: 10px"
          >
            <a-row :gutter="24">
              <a-col :span="6">
                <a-form-item field="status" :label="`状态`">
                  <a-select v-model="formModel.status" placeholder="请选择 ...">
                    <a-option :value="1">启用</a-option>
                    <a-option :value="0">禁用</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="10">
                <a-form-item field="status" label="货品">
                  <a-auto-complete
                    v-model="formModel.title"
                    :data="searchData"
                    :allow-clear="true"
                    placeholder="货品名称 / 货品代码 / 供应商"
                    :filter-option="() => true"
                    @search="handleSearchKey"
                  >
                  </a-auto-complete>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item field="zeroStock" label="零库存">
                  <a-space>
                    <a-switch
                      v-model="formModel.zeroStock"
                      @change="handleZeroStockChange"
                    />
                  </a-space>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-col>
        <a-col :span="4">
          <a-space direction="horizontal" :size="18" style="margin-top: 10px">
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

            <a-button @click="exportXls(formModel)">
              <template #icon>
                <icon-download />
              </template>
              导出
            </a-button>
            <a-select
              v-if="selectedRowKeys.length > 0"
              placeholder="批量操作"
              style="width: 120px"
              @change="batchChange"
            >
              <a-option :value="0">禁用</a-option>
              <a-option :value="1">启用</a-option>
              <a-option :value="2">移动</a-option>
              <a-option :value="3">删除</a-option>
            </a-select>
            <span v-if="selectedRowKeys.length > 0" style="margin-left: 6px"
              >已选中{{ selectedRowKeys.length }}条数据</span
            >
            <a-button
              v-if="selectedRowKeys.length > 0"
              type="text"
              size="small"
              @click="selectedRowKeys = []"
              >清空</a-button
            >
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
        :size="size"
        :scrollbar="true"
        :scroll="{ x: '100%', y: getAdaptiveTableScrollY(540) }"
        :row-selection="rowSelection"
        @selection-change="selectChange"
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
        <template #salePrc="{ record }">
          <span>￥{{ formatPrice(record.salePrc) }}</span>
        </template>

        <template #tradePrc="{ record }">
          <span>￥{{ formatPrice(record.tradePrc) }}</span>
        </template>

        <template #purPrc="{ record }">
          <span>￥{{ formatPrice(record.purPrc) }}</span>
        </template>

        <template #stockCost="{ record }">
          <span>￥{{ formatPrice(record.stockCost) }}</span>
        </template>

        <template #costPrice="{ record }">
          <span>￥{{ formatPrice(record.costPrice) }}</span>
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
          <a-divider style="margin: 0" direction="vertical" />
          <a-button type="text" size="small" @click="handleStockDetail(record)">
            库存明细
          </a-button>
        </template>
      </a-table>
    </div>
    <form-modal ref="modalRef" @ok="search(pagination)"></form-modal>
    <default-modal ref="defModal" @ok="doMore" />
    <category-select ref="categoryRef" @ok="categoryChange" />
    <stock-detail-modal ref="stockDetailRef" />
  </div>
</template>

<script lang="ts" setup>
  import getAdaptiveTableScrollY from '@/hooks/table-scroll';
  import { computed, nextTick, reactive, ref } from 'vue';
  import { Pagination } from '@/types/global';
  import useLoading from '@/hooks/loading';
  import { PolicyParams } from '@/api/list';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import { Message } from '@arco-design/web-vue';
  import { formatPrice } from '@/api/common';
  import { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import DefaultModal from '@/components/default-modal.vue';
  import CategorySelect from '@/views/app/goods/components/category-select.vue';
  import FormModal from './goods-modal.vue';
  import StockDetailModal from './stock-detail-modal.vue';

  import {
    listPage,
    remove,
    exportXlsFile,
    importExcel,
    searchKey,
    batchUpdateCategory,
    batchRemove,
    batchChangeStatus,
  } from '../api/api-AppGoods';
  import type { AppGoods } from '../types/AppGoods';

  const { loading, setLoading } = useLoading(false);
  type SizeProps = 'mini' | 'small' | 'medium' | 'large';
  const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
  const uploadLoading = ref<boolean>(false);
  const searchData = ref<GoodsSearchResult[]>([]);
  const selectedRowKeys = ref([]);
  const defModal = ref<InstanceType<typeof DefaultModal> | null>(null);
  const categoryRef = ref<InstanceType<typeof CategorySelect> | null>(null);
  const stockDetailRef = ref<InstanceType<typeof StockDetailModal> | null>(
    null
  );

  const rowSelection = reactive({
    selectedRowKeys,
    type: 'checkbox',
    showCheckedAll: true,
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

  const generateFormModel = (): AppGoods => {
    return {
      id: undefined,
      title: undefined,
      supplierTitle: undefined,
      categoryId: undefined,
      goodsCode: undefined,
      imgUrl: undefined,
      initCost: undefined,
      initStock: undefined,
      stock: undefined,
      stockCost: undefined,
      costPrice: undefined,
      unit: undefined,
      salePrc: undefined,
      tradePrc: undefined,
      purPrc: undefined,
      maxStock: undefined,
      minStock: undefined,
      supplierId: undefined,
      note: undefined,
      status: undefined,
      zeroStock: true,
      createTime: undefined,
      updateTime: undefined,
    };
  };

  const formModel = ref(generateFormModel());
  const size = ref<SizeProps>('medium');
  const renderData = ref<AppGoods[]>([]);

  const basePagination: Pagination = {
    current: 1,
    pageSize: 50,
    total: 0,
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
      title: '货品名称',
      dataIndex: 'title',
      align: 'center',
      width: 220,
      ellipsis: true,
      tooltip: true,
    },
    {
      title: '货品分类',
      dataIndex: 'categoryId_dictText',
      align: 'center',
    },
    {
      title: '单位',
      dataIndex: 'unit',
      align: 'center',
    },

    {
      title: '库存',
      dataIndex: 'stock',
      align: 'center',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '库存总成本',
      dataIndex: 'stockCost',
      align: 'center',
      slotName: 'stockCost',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '成本价',
      dataIndex: 'costPrice',
      align: 'center',
      slotName: 'costPrice',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '零售价',
      dataIndex: 'salePrc',
      align: 'center',
      slotName: 'salePrc',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '批发价',
      dataIndex: 'tradePrc',
      align: 'center',
      slotName: 'tradePrc',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },
    {
      title: '进货价',
      dataIndex: 'purPrc',
      align: 'center',
      slotName: 'purPrc',
      sortable: {
        sorter: true,
        sortDirections: ['ascend', 'descend'],
      },
    },

    /* {
      title: '状态',
      dataIndex: 'status',
      align: 'center',
      slotName: 'status',
    }, */
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      align: 'center',
      width: 280,
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
    if (!p) {
      selectedRowKeys.value = [];
    }
    const pg: Pagination = p || basePagination;
    fetchData({
      ...pg,
      ...formModel.value,
    } as unknown as PolicyParams);
  };

  const onPageChange = (current: number) => {
    fetchData({ ...basePagination, ...formModel.value, current });
  };

  const handleZeroStockChange = () => {
    search();
  };

  search();
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

  const handelRemove = async (data: AppGoods) => {
    await remove(data);
    Message.success('操作成功');
    search();
  };

  const handelEdit = (item: AppGoods) => {
    modalRef.value?.showModal(item);
  };

  const handleStockDetail = (item: AppGoods) => {
    stockDetailRef.value?.showModal(item);
  };

  const dbRowClick = (record: AppGoods, rowIndex: number) => {
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

  const queryByCategoryId = (categoryId: number) => {
    if (categoryId !== 0) {
      formModel.value.categoryId = categoryId;
    } else {
      formModel.value.categoryId = undefined;
    }

    search();
  };

  const handleSearchKey = async (key: any) => {
    if (!key) {
      searchData.value = [];
      return;
    }
    const res = await searchKey(key);
    searchData.value = res.data;
  };

  const selectChange = (e: []) => {
    selectedRowKeys.value = e;
  };

  const exportXls = (data: AppGoods) => {
    if (selectedRowKeys.value.length <= 0) {
      Message.warning('请先选中要导出的数据。');
      return;
    }
    data.selections = selectedRowKeys.value.join(',');
    exportXlsFile(data);
  };

  const batchChange = (e: number) => {
    let msg = '确认禁用选中数据？';
    switch (e) {
      case 1:
        msg = '确认启用选中数据？';
        break;
      case 3:
        msg = '确认删除选中数据？';
        break;
      default:
        break;
    }
    if (e !== 2) {
      defModal.value?.openModal('提示', msg, e);
    } else {
      categoryRef.value?.showModal();
    }
  };

  const doMore = async (e: any) => {
    if (e !== -1) {
      if (e === 0 || e === 1) {
        await batchChangeStatus(e, selectedRowKeys.value.join(','));
      } else if (e === 3) {
        await batchRemove(selectedRowKeys.value.join(','));
      }
      Message.success('操作成功');
      search(pagination);
    }

    selectedRowKeys.value = [];
  };

  const categoryChange = async (categoryId: number) => {
    await batchUpdateCategory(categoryId, selectedRowKeys.value.join(','));
    Message.success('操作成功');
    search(pagination);
    selectedRowKeys.value = [];
  };
  defineExpose({ queryByCategoryId });
</script>

<style lang="less" scoped>
  .container {
  }
</style>
