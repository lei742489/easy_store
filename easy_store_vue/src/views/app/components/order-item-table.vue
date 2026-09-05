<template>
  <div ref="itemFormRef" class="item-form">
    <a-table
      ref="goodsTableRef"
      :columns="columns"
      :data="data"
      :pagination="false"
      size="small"
      :scroll="{ x: '100%', y: props.entryStyle ? 320 : 258 }"
      :scrollbar="true"
      :hoverable="false"
      :summary="true"
      :class="{ 'entry-style-table': props.entryStyle }"
      :row-class="orderRowClass"
      @row-click="handleOrderRowClick"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>
      <template #goodsCode="{ rowIndex }">
        <div :data-goods-code-row="rowIndex">
          <a-input
            v-model="data[rowIndex].goodsCode"
            allow-clear
            :placeholder="props.entryStyle ? '请输入货品代码' : ''"
            @focus="handleGoodsCodeInputFocus(rowIndex)"
            @input="(value: string) => handleGoodsCodeSearch(rowIndex, value)"
            @keydown.down.prevent="moveGoodsHighlight(1)"
            @keydown.up.prevent="moveGoodsHighlight(-1)"
            @press-enter="confirmHighlightedGoods"
          />
        </div>
      </template>
      <template #goodsId="{ rowIndex }">
        <div
          v-if="props.goodsSearchPanel"
          :data-goods-name-row="rowIndex"
          style="display: flex; align-items: center; gap: 5px"
        >
          <a-popover
            v-if="data[rowIndex].panelSelectedGoods"
            class="goods-input-popover"
            trigger="hover"
            position="top"
          >
            <a-input
              v-model="data[rowIndex].goodsId_dictText"
              allow-clear
              :placeholder="props.entryStyle ? '请输入货品名称' : ''"
              @focus="handleGoodsInputFocus(rowIndex)"
              @input="(value: string) => handlePanelSearch(rowIndex, value)"
              @keydown.down.prevent="moveGoodsHighlight(1)"
              @keydown.up.prevent="moveGoodsHighlight(-1)"
              @press-enter="confirmHighlightedGoods"
            />
            <template #content>
              <span class="stock-popover-text">
                当前库存：{{ formatPrice(data[rowIndex].stock || 0) }}
              </span>
            </template>
          </a-popover>
          <a-input
            v-else
            v-model="data[rowIndex].goodsId_dictText"
            allow-clear
            :placeholder="props.entryStyle ? '请输入货品名称' : ''"
            @focus="handleGoodsInputFocus(rowIndex)"
            @input="(value: string) => handlePanelSearch(rowIndex, value)"
            @keydown.down.prevent="moveGoodsHighlight(1)"
            @keydown.up.prevent="moveGoodsHighlight(-1)"
            @press-enter="confirmHighlightedGoods"
          />
        </div>
        <div v-else style="display: flex; align-items: center; gap: 5px">
          <a-auto-complete
            v-model="data[rowIndex].goodsId_dictText"
            :data="goodsSearchData"
            :allow-clear="true"
            placeholder=""
            :filter-option="() => true"
            @select="(value:any) => selectGoods(data[rowIndex], value)"
            @search="handleSearchKey"
            @dropdown-reach-bottom="() => loadGoodsMore(data[rowIndex])"
          >
          </a-auto-complete>
        </div>
      </template>
      <template #unit="{ rowIndex }">
        <a-select
          v-model="data[rowIndex].unit"
          :options="unitList"
          :field-names="{ value: 'name', label: 'name' }"
          :allow-search="true"
          :allow-clear="true"
          :placeholder="props.entryStyle ? '请选择' : ''"
        >
        </a-select>
      </template>

      <template #category="{ rowIndex }">
        <category-select-tree
          v-model:category-id="data[rowIndex].categoryId"
          v-model:category-text="data[rowIndex].categoryId_dictText"
        ></category-select-tree>
      </template>

      <template #quantity="{ rowIndex }">
        <a-input-number
          v-if="orderType == 1"
          v-model="data[rowIndex].quantity"
          :precision="2"
          :placeholder="props.entryStyle ? '0' : ''"
          @change="updateTotalAmount(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].quantity"
          :precision="2"
          style="color: red"
          :placeholder="props.entryStyle ? '0' : ''"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 1)"
        ></a-input-number>
      </template>

      <template #unitPrice="{ rowIndex }">
        <a-tooltip
          :disabled="!isBelowCostPrice(data[rowIndex])"
          :content="getEstimatedLossText(data[rowIndex])"
          background-color="#e74c3c"
          position="top"
        >
          <div
            :class="{
              'below-cost-price': isBelowCostPrice(data[rowIndex]),
            }"
          >
            <a-input-number
              v-model="data[rowIndex].unitPrice"
              :precision="2"
              :placeholder="props.entryStyle ? '0.00' : ''"
              @change="updateTotalAmount(data[rowIndex])"
            ></a-input-number>
          </div>
        </a-tooltip>
      </template>

      <template #totalAmount="{ rowIndex }">
        <a-input-number
          v-if="orderType == 1"
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          :placeholder="props.entryStyle ? '0.00' : ''"
          @change="updateUnitPrice(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          :placeholder="props.entryStyle ? '0.00' : ''"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 2)"
        ></a-input-number>
      </template>

      <template #note="{ rowIndex }">
        <a-input
          v-model="data[rowIndex].note"
          :placeholder="props.entryStyle ? '请输入备注' : ''"
        ></a-input>
      </template>

      <template #operations="{ rowIndex }">
        <a-popconfirm content="确认删除该条数据?" @ok="handelRemove(rowIndex)">
          <a-button v-if="props.entryStyle" type="text" size="small">
            <template #icon><icon-delete /></template>
          </a-button>
          <a-button v-else type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>

      <template #summary-cell="{ column, record }">
        <div v-if="column.dataIndex === 'index'">
          <span v-if="props.entryStyle" class="entry-row-count">
            共{{ data.length }}行
          </span>
        </div>
        <div v-if="column.dataIndex == 'categoryId'">
          <div style="width: 100%; text-align: right">合计:</div>
        </div>
        <div
          v-if="
            column.dataIndex === 'quantity' ||
            column.dataIndex === 'totalAmount'
          "
          >{{ column.dataIndex === 'totalAmount' ? '￥' : '' }}
          {{ formatPrice(record[column.dataIndex]) }}</div
        >
      </template>
    </a-table>
    <div
      v-if="props.goodsSearchPanel && goodsPanelVisible"
      class="goods-search-panel"
      :style="{
        top: `${goodsPanelTop}px`,
        left: `${goodsPanelLeft}px`,
        width: `${goodsPanelWidth}px`,
        maxWidth: `${goodsPanelWidth}px`,
      }"
      @mousedown.stop
    >
      <div class="goods-search-table">
        <div
          class="goods-search-header goods-search-row"
          :class="{ simple: isPurchaseGoodsSearch }"
        >
          <div>货品类别</div>
          <div>品名规格</div>
          <template v-if="!isPurchaseGoodsSearch">
            <div>零售价</div>
            <div>批发价</div>
          </template>
          <div>库存</div>
        </div>
        <div class="goods-search-body">
          <div
            v-for="(goods, index) in goodsSearchData"
            :key="`${goods.goodsId || goods.value}-${index}`"
            class="goods-search-row"
            :class="{
              active: highlightedGoodsIndex === index,
              simple: isPurchaseGoodsSearch,
            }"
            @click="highlightedGoodsIndex = index"
            @dblclick="confirmGoods(goods)"
          >
            <div>{{ goods.categoryName || '' }}</div>
            <div class="goods-name">
              <a-tooltip
                :content="goods.value || goods.label || ''"
                position="top"
              >
                <span class="goods-name-text">
                  {{ goods.value || goods.label || '' }}
                </span>
              </a-tooltip>
            </div>
            <template v-if="!isPurchaseGoodsSearch">
              <div class="price">{{ formatPrice(goods.salePrc || 0) }}</div>
              <div class="price">{{ formatPrice(goods.tradePrc || 0) }}</div>
            </template>
            <div class="price">{{ formatPrice(goods.stock || 0) }}</div>
          </div>
          <div v-if="goodsSearchData.length === 0" class="goods-empty">
            没有匹配的货品
          </div>
        </div>
      </div>
      <div class="goods-search-footer">
        <a-checkbox
          v-if="showZeroStockFilter"
          v-model="hideZeroStock"
          @change="refreshGoodsPanel"
        >
          隐藏零库存商品
        </a-checkbox>
        <span v-else></span>
        <a-button type="primary" size="small" @click="confirmHighlightedGoods">
          确定
        </a-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import {
    computed,
    ref,
    nextTick,
    watch,
    onBeforeUnmount,
    onMounted,
  } from 'vue';
  import { GoodsSearchResult } from '@/views/app/goods/types/GoodsSearchResult';
  import { getUnitList, searchKey } from '@/views/app/goods/api/api-AppGoods';
  import { mulPrice, formatPrice, divPrice, addPrice } from '@/api/common';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import type { AppUnit } from '@/views/app/goods/types/AppGoods';
  import CategorySelectTree from '@/views/app/goods/components/category-select-tree.vue';

  const props = withDefaults(
    defineProps<{
      goodsSearchPanel?: boolean;
      goodsSearchType?: 'sale' | 'purchase';
      entryStyle?: boolean;
      initialRows?: number;
      showGoodsCode?: boolean;
      isRoot?: boolean;
    }>(),
    {
      goodsSearchPanel: false,
      goodsSearchType: 'sale',
      entryStyle: false,
      initialRows: 10,
      showGoodsCode: true,
      isRoot: false,
    }
  );

  export interface OrderItemRow {
    id?: number;
    goodsId?: number | string;
    goodsId_dictText?: string;
    goodsCode?: string;
    goodsName?: string;
    categoryId?: number;
    categoryId_dictText?: string;
    unit?: string;
    quantity?: number;
    unitPrice?: number;
    stock?: number;
    panelSelectedGoods?: boolean;
    totalAmount?: number;
    totalAmountEdited?: boolean;
    costPrice?: number;
    orderId?: number;
    note?: string;
    createTime?: string | Date;
    updateTime?: string | Date;
  }

  const data = ref<OrderItemRow[]>([]);
  const goodsSearchData = ref<GoodsSearchResult[]>([]);
  const unitList = ref<AppUnit[]>([]);
  const orderType = defineModel<number>('orderType');
  const defaultItem: OrderItemRow = {
    id: undefined,
    goodsId: undefined,
    goodsId_dictText: undefined,
    categoryId: 0,
    unit: undefined,
    quantity: undefined,
    unitPrice: undefined,
    stock: undefined,
    panelSelectedGoods: false,
    totalAmount: undefined,
    totalAmountEdited: false,
    costPrice: undefined,
    note: undefined,
  };
  const goodsPageNo = ref(1);
  const itemFormRef = ref<HTMLElement | null>(null);
  const activeGoodsRowIndex = ref<number | undefined>();
  const goodsPanelVisible = ref(false);
  const goodsPanelTop = ref(0);
  const goodsPanelLeft = ref(0);
  const goodsPanelWidth = ref(0);
  const activeOrderRow = ref<OrderItemRow | null>(null);
  const highlightedGoodsIndex = ref(0);
  const hideZeroStock = ref(true);
  const lastSearchToken = ref(0);
  const activeGoodsSearchField = ref<'name' | 'code'>('name');
  const isPurchaseGoodsSearch = props.goodsSearchType === 'purchase';
  const showZeroStockFilter = props.goodsSearchType === 'sale';

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 60,
    },
    ...(props.showGoodsCode
      ? [
          {
            title: '货品代码',
            dataIndex: 'goodsCode',
            align: 'center' as const,
            width: 150,
            slotName: 'goodsCode',
          },
        ]
      : []),
    {
      title: '货品名称',
      dataIndex: 'goodsId',
      align: 'center',
      minWidth: 360,
      slotName: 'goodsId',
    },
    {
      title: '单位',
      dataIndex: 'unit',
      align: 'center',
      slotName: 'unit',
      width: 140,
    },
    {
      title: '货品类别',
      dataIndex: 'categoryId',
      align: 'center',
      slotName: 'category',
      width: 160,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      align: 'center',
      slotName: 'quantity',
      width: 100,
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      align: 'center',
      slotName: 'unitPrice',
      width: 150,
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      align: 'center',
      slotName: 'totalAmount',
      width: 150,
    },
    {
      title: '备注',
      dataIndex: 'note',
      align: 'center',
      slotName: 'note',
      width: 150,
    },
    {
      title: '操作',
      align: 'center',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 80,
      fixed: props.entryStyle ? 'right' : undefined,
    },
  ]);

  const emit = defineEmits<{
    (e: 'change', changeDate: number): void;
  }>();

  const goodsTableRef = ref();

  const scrollToBottom = () => {
    nextTick(() => {
      const wrapper =
        goodsTableRef.value?.$el?.querySelector('.arco-table-body');
      if (wrapper) {
        wrapper.scrollTop = wrapper.scrollHeight;
      }
    });
  };

  const addItem = (par?: number) => {
    const item: OrderItemRow = {};
    Object.assign(item, defaultItem);
    data.value.push(item);
    if (par !== undefined) scrollToBottom();
  };

  const fetchUnitData = async () => {
    if (unitList.value.length !== 0) return;
    unitList.value = (await getUnitList()).data;
  };

  const clearSearchData = () => {
    lastSearchToken.value += 1;
    goodsSearchData.value = [];
    goodsPageNo.value = 1;
    highlightedGoodsIndex.value = 0;
  };

  const handleSearchKey = async (key: any) => {
    if (!key) {
      clearSearchData();
      return;
    }
    goodsPageNo.value = 1;
    const res = await searchKey(key, 1, undefined, 'name');
    goodsSearchData.value = res.data;
  };

  const updateGoodsPanelPosition = (
    rowIndex: number,
    searchField: 'name' | 'code' = activeGoodsSearchField.value
  ) => {
    nextTick(() => {
      const root = itemFormRef.value;
      const inputWrap = root?.querySelector(
        `[data-goods-${searchField}-row="${rowIndex}"]`
      ) as HTMLElement | null;
      if (!root || !inputWrap) return;
      const rootRect = root.getBoundingClientRect();
      const inputRect = inputWrap.getBoundingClientRect();
      const tableRect =
        (goodsTableRef.value?.$el as HTMLElement | undefined)
          ?.getBoundingClientRect?.() || rootRect;
      const width = Math.min(tableRect.width * 0.5, rootRect.width);
      const left = Math.max(
        0,
        Math.min(inputRect.left - rootRect.left, rootRect.width - width)
      );
      goodsPanelTop.value = inputRect.bottom - rootRect.top + 2;
      goodsPanelLeft.value = left;
      goodsPanelWidth.value = width;
    });
  };

  const queryPanelGoods = async (
    key: string,
    searchField: 'name' | 'code'
  ) => {
    const token = lastSearchToken.value + 1;
    lastSearchToken.value = token;
    goodsPageNo.value = 1;
    const res = await searchKey(
      key,
      1,
      showZeroStockFilter ? hideZeroStock.value : undefined,
      searchField
    );
    if (token !== lastSearchToken.value) return;
    goodsSearchData.value = res.data;
    highlightedGoodsIndex.value = 0;
    goodsPanelVisible.value = true;
  };

  const handleGoodsInputFocus = (rowIndex: number) => {
    activeGoodsRowIndex.value = rowIndex;
    activeGoodsSearchField.value = 'name';
    updateGoodsPanelPosition(rowIndex, 'name');
    const key = data.value[rowIndex].goodsId_dictText || '';
    clearSearchData();
    if (key.trim()) queryPanelGoods(key, 'name');
  };

  const handleGoodsCodeInputFocus = (rowIndex: number) => {
    activeGoodsRowIndex.value = rowIndex;
    activeGoodsSearchField.value = 'code';
    updateGoodsPanelPosition(rowIndex, 'code');
    const key = data.value[rowIndex].goodsCode || '';
    clearSearchData();
    if (key.trim()) queryPanelGoods(key, 'code');
  };

  const handlePanelSearch = async (rowIndex: number, value: string) => {
    await handleGoodsSearch(rowIndex, value, 'name');
  };

  const handleGoodsCodeSearch = async (rowIndex: number, value: string) => {
    await handleGoodsSearch(rowIndex, value, 'code');
  };

  const handleGoodsSearch = async (
    rowIndex: number,
    value: string,
    searchField: 'name' | 'code'
  ) => {
    activeGoodsRowIndex.value = rowIndex;
    activeGoodsSearchField.value = searchField;
    updateGoodsPanelPosition(rowIndex, searchField);
    const row = data.value[rowIndex];
    const wasSelected = row.panelSelectedGoods;
    row.goodsId = undefined;
    if (searchField === 'name') {
      row.goodsId_dictText = value;
      row.goodsName = value;
      if (wasSelected) row.goodsCode = undefined;
    } else {
      row.goodsCode = value;
      if (wasSelected) {
        row.goodsId_dictText = undefined;
        row.goodsName = undefined;
      }
    }
    row.unit = undefined;
    row.categoryId = undefined;
    row.categoryId_dictText = undefined;
    data.value[rowIndex].stock = undefined;
    row.costPrice = undefined;
    row.unitPrice = undefined;
    row.totalAmount = undefined;
    row.totalAmountEdited = false;
    data.value[rowIndex].panelSelectedGoods = false;
    emitChange();
    if (!value || !value.trim()) {
      clearSearchData();
      goodsPanelVisible.value = false;
      return;
    }
    await queryPanelGoods(value, searchField);
  };

  const refreshGoodsPanel = async () => {
    const rowIndex = activeGoodsRowIndex.value;
    if (rowIndex === undefined) return;
    const key = data.value[rowIndex].goodsId_dictText || '';
    const searchField = activeGoodsSearchField.value;
    const searchKeyValue = searchField === 'code'
      ? data.value[rowIndex].goodsCode || ''
      : key;
    if (!searchKeyValue.trim()) return;
    await queryPanelGoods(searchKeyValue, searchField);
  };

  const hideGoodsPanel = () => {
    goodsPanelVisible.value = false;
  };

  const moveGoodsHighlight = (step: number) => {
    if (!goodsPanelVisible.value || goodsSearchData.value.length === 0) return;
    const nextIndex = highlightedGoodsIndex.value + step;
    if (nextIndex < 0) {
      highlightedGoodsIndex.value = goodsSearchData.value.length - 1;
    } else if (nextIndex >= goodsSearchData.value.length) {
      highlightedGoodsIndex.value = 0;
    } else {
      highlightedGoodsIndex.value = nextIndex;
    }
  };

  const getPanelUnitPrice = (goods: GoodsSearchResult) => {
    if (isPurchaseGoodsSearch) {
      return goods.purPrc ?? goods.tradePrc ?? goods.salePrc;
    }
    return goods.salePrc ?? goods.tradePrc ?? goods.purPrc;
  };

  const isBelowCostPrice = (goodsItem: OrderItemRow) => {
    if (!props.isRoot || !props.goodsSearchPanel || isPurchaseGoodsSearch) {
      return false;
    }
    const unitPrice = Number(goodsItem.unitPrice);
    const costPrice = Number(goodsItem.costPrice);
    return (
      Number.isFinite(unitPrice) &&
      Number.isFinite(costPrice) &&
      unitPrice < costPrice
    );
  };

  const getEstimatedLossText = (goodsItem: OrderItemRow) => {
    const quantity = Math.abs(Number(goodsItem.quantity) || 0);
    const unitPrice = Number(goodsItem.unitPrice) || 0;
    const costPrice = Number(goodsItem.costPrice) || 0;
    return `预计亏损 ${formatPrice((costPrice - unitPrice) * quantity)}`;
  };

  const handleDocumentMouseDown = (event: MouseEvent) => {
    const root = itemFormRef.value;
    const target = event.target as Node;
    const targetElement = target instanceof Element ? target : null;
    const row = targetElement?.closest('.arco-table-tbody .arco-table-tr');
    const tabBar = targetElement?.closest('.tab-bar-container');
    const panel = root?.querySelector('.goods-search-panel');
    const inputWrap = root?.querySelector(
      `[data-goods-${activeGoodsSearchField.value}-row="${activeGoodsRowIndex.value}"]`
    );
    if (!root) return;
    if (tabBar) {
      hideGoodsPanel();
      return;
    }
    if (!row && !panel?.contains(target)) {
      activeOrderRow.value = null;
    }
    if (panel?.contains(target) || inputWrap?.contains(target)) return;
    hideGoodsPanel();
  };

  const handleOrderRowClick = (record: OrderItemRow) => {
    activeOrderRow.value = record;
  };

  const orderRowClass = (record: OrderItemRow) => {
    return activeOrderRow.value === record ? 'active-order-item-row' : '';
  };

  const initItemList = (size: number) => {
    if (size <= 0) {
      const initNum = props.initialRows;
      for (let i = 0; i < initNum; i += 1) {
        addItem();
      }
    }
  };

  const initData = async (itemList: OrderItemRow[]) => {
    fetchUnitData();
    if (itemList.length > 0) {
      data.value = itemList.map((item) => {
        const { goodsId } = item;
        const goodsName = (item.goodsName || '').trim();
        if (!item.goodsId_dictText && goodsName) {
          return {
            ...item,
            goodsId_dictText: goodsName,
          };
        }
        if (
          !item.goodsId_dictText &&
          typeof goodsId === 'string' &&
          goodsId.trim() &&
          !/^\d+$/.test(goodsId.trim())
        ) {
          return {
            ...item,
            goodsName: goodsId.trim(),
            goodsId_dictText: goodsId.trim(),
          };
        }
        return item;
        });
      initItemList(itemList.length);
    } else {
      data.value = [];
      initItemList(0);
    }
    activeOrderRow.value = null;
    const res = await searchKey('', 1);
    goodsSearchData.value = res.data;
  };

  const loadGoodsMore = async (goodsItem: OrderItemRow) => {
    goodsPageNo.value += 1;
    const res = await searchKey(
      activeGoodsSearchField.value === 'code'
        ? goodsItem.goodsCode || ''
        : goodsItem.goodsId_dictText || '',
      goodsPageNo.value,
      undefined,
      activeGoodsSearchField.value
    );
    if (res.data.length > 0) {
      goodsSearchData.value = goodsSearchData.value.concat(res.data);
    }
  };

  function emitChange() {
    let tList = 0;
    data.value.forEach((item) => {
      const totalAmount =
        item.totalAmount !== undefined && item.totalAmount !== null
          ? item.totalAmount
          : mulPrice(item.quantity || 0, item.unitPrice || 0);
      tList = addPrice(
        tList,
        totalAmount || 0
      );
    });
    emit('change', tList);
  }

  function updateTotalAmount(goodsItem: OrderItemRow) {
    goodsItem.totalAmount = mulPrice(
      goodsItem.quantity || 0,
      goodsItem.unitPrice || 0
    );
    goodsItem.totalAmountEdited = false;
    emitChange();
  }

  const confirmGoods = (goods: GoodsSearchResult) => {
    const rowIndex = activeGoodsRowIndex.value;
    if (rowIndex === undefined || !goods) return;
    const row = data.value[rowIndex];
    if (!row) return;
    const goodsName = goods.value || goods.label || '';
    row.goodsId = goods.goodsId;
    row.goodsCode = goods.goodsCode;
    row.goodsId_dictText = goodsName;
    row.goodsName = goodsName;
    row.unit = goods.unit;
    row.categoryId = goods.categoryId;
    row.categoryId_dictText = goods.categoryName;
    row.stock = goods.stock;
    row.costPrice = goods.costPrice;
    row.panelSelectedGoods = true;
    row.quantity = orderType.value === 1 ? 1 : -1;
    row.unitPrice = getPanelUnitPrice(goods);
    row.totalAmountEdited = false;
    if (row.unitPrice !== undefined) {
      updateTotalAmount(row);
    } else {
      row.totalAmount = undefined;
      emitChange();
    }
    hideGoodsPanel();
  };

  const confirmHighlightedGoods = () => {
    const goods = goodsSearchData.value[highlightedGoodsIndex.value];
    if (goods) confirmGoods(goods);
  };

  const updateUnitPrice = (
    goodsItem: OrderItemRow,
    markTotalEdited = true
  ) => {
    const quantity = Number(goodsItem.quantity || 0);
    const totalAmount = Number(goodsItem.totalAmount || 0);
    goodsItem.unitPrice =
      Number.isFinite(quantity) && quantity !== 0 && Number.isFinite(totalAmount)
        ? divPrice(totalAmount, quantity)
        : 0;
    if (markTotalEdited) goodsItem.totalAmountEdited = true;
    emitChange();
  };

  const selectGoods = (goodsItem: OrderItemRow, value: any) => {
    const option = goodsSearchData.value.find((item) => item.value === value);
    if (option) {
      const {
        goodsId,
        unit,
        categoryId,
        categoryName,
        stock,
        value: goodsName,
      } = option;
      if (goodsId == null) return;
      goodsItem.goodsId = goodsId;
      goodsItem.goodsCode = option.goodsCode;
      goodsItem.goodsId_dictText = goodsName || option.label || '';
      goodsItem.goodsName = goodsItem.goodsId_dictText;
      goodsItem.unit = unit;
      goodsItem.categoryId = categoryId;
      goodsItem.quantity = orderType.value === 1 ? 1 : -1;
      goodsItem.categoryId_dictText = categoryName;
      goodsItem.stock = stock;
      goodsItem.costPrice = option.costPrice;
      goodsItem.unitPrice = undefined;
      goodsItem.totalAmount = undefined;
      goodsItem.totalAmountEdited = false;
      emitChange();
    }
  };

  const handelRemove = (idx: number) => {
    data.value.splice(idx, 1);
    emitChange();
  };

  const getItemsList = () => {
    const resultList: OrderItemRow[] = [];
    data.value.forEach((item) => {
      const goodsName = (item.goodsId_dictText || '').trim();
      if (item.goodsId || goodsName) {
        resultList.push({
          ...item,
          goodsName,
        });
      }
    });
    return resultList;
  };

  const handleNegativeChange = (
    val: number | undefined,
    rowIndex: number,
    type: number
  ) => {
    const inputVal = val || 0;
    const negativeVal = inputVal > 0 ? -inputVal : inputVal;
    if (type === 1) {
      data.value[rowIndex].quantity = negativeVal;
      updateTotalAmount(data.value[rowIndex]);
    } else {
      data.value[rowIndex].totalAmount = negativeVal;
      updateUnitPrice(data.value[rowIndex]);
    }
  };

  const clearAll = () => {
    data.value = [];
    activeOrderRow.value = null;
    initItemList(0);
    emitChange();
  };

  watch(orderType, (newVal) => {
    data.value.forEach((item) => {
      if (item.goodsId !== undefined) {
        if (newVal === 2) {
          item.quantity = item.quantity ? -item.quantity : 0;
          item.totalAmount = item.totalAmount ? -item.totalAmount : 0;
        } else {
          item.quantity = Math.abs(item.quantity || 0);
          item.totalAmount = Math.abs(item.totalAmount || 0);
        }

        updateUnitPrice(item, false);
      }
    });
  });

  onMounted(() => {
    document.addEventListener('mousedown', handleDocumentMouseDown);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleDocumentMouseDown);
  });

  defineExpose({ getItemsList, initData, clearAll, addItem });
</script>

<style lang="less" scoped>
  .item-form {
    position: relative;
    width: 100%;
    min-height: 240px;

    :deep(.arco-input-wrapper),
    :deep(.arco-input-number),
    :deep(.arco-select),
    :deep(.arco-select-view),
    :deep(.arco-select-view-single) {
      width: 100%;
      min-width: 0;
    }

    :deep(.goods-input-popover) {
      width: 100%;
    }

    :deep(.entry-style-table .arco-table-th) {
      color: var(--color-text-2);
      font-weight: 500;
      background: var(--color-fill-2);
    }

    :deep(.entry-style-table.arco-table-size-small .arco-table-cell) {
      padding: 8px 8px;
    }

    :deep(.entry-style-table .arco-input-wrapper),
    :deep(.entry-style-table .arco-input-number),
    :deep(.entry-style-table .arco-select-view) {
      min-height: 36px;
      background: var(--color-bg-1);
      border-color: var(--color-neutral-3);
      border-radius: 4px;
    }

    :deep(.entry-style-table .arco-input-number-input),
    :deep(.entry-style-table .arco-input),
    :deep(.entry-style-table .arco-select-view-value) {
      font-size: 14px;
    }

    :deep(.entry-style-table .arco-table-summary) {
      color: var(--color-text-1);
      font-weight: 500;
      background: var(--color-fill-1);
    }

    :deep(.entry-style-table .arco-table-summary .arco-table-td) {
      height: 35px;
      padding: 0 8px;
      line-height: 35px;
      white-space: nowrap;
    }

    :deep(.active-order-item-row > .arco-table-td) {
      background: rgb(var(--purple-1),0.7);
    }

  }

  .entry-row-count {
    color: var(--color-text-1);
    white-space: nowrap;
  }

  .goods-search-panel {
    position: absolute;
    z-index: 20;
    border: 1px solid var(--color-border-3);
    background: var(--color-bg-popup);
    color: var(--color-text-1);
    box-shadow: 0 6px 18px rgb(0 0 0 / 12%);
  }

  .goods-search-table {
    width: 100%;
    overflow: hidden;
  }

  .goods-search-row {
    display: grid;
    grid-template-columns: 18.8% minmax(160px, 1fr) 15.8% 15.8% 14.8%;
    min-height: 30px;
    color: var(--color-text-1);

    > div {
      overflow: hidden;
      padding: 5px 8px;
      border-right: 1px solid var(--color-border-2);
      border-bottom: 1px solid var(--color-border-2);
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    > div:last-child {
      border-right: 0;
    }
  }

  .goods-search-row.simple {
    grid-template-columns: 120px minmax(160px, 1fr) 100px;
  }

  .goods-search-header {
    color: var(--color-text-1);
    background: var(--color-fill-2);
    font-weight: 500;
  }

  .goods-search-body {
    max-height: 260px;
    overflow: auto;
  }

  .goods-search-body .goods-search-row {
    cursor: pointer;
  }

  .goods-search-body .goods-search-row.active {
    background: rgb(var(--arcoblue-1));
  }

  .goods-search-body .goods-search-row:hover {
    background: rgb(var(--arcoblue-1));
  }

  .goods-name {
    text-align: left;
  }

  .goods-name-text {
    display: block;
    overflow: hidden;
    color: var(--color-text-1);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .price {
    color: var(--color-text-1);
    text-align: right;
  }

  .below-cost-price :deep(.arco-input-number-input),
  .below-cost-price :deep(input) {
    color: rgb(var(--arcoblue-6));
  }

  .goods-empty {
    padding: 18px;
    color: var(--color-text-3);
    text-align: center;
  }

  .goods-search-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 12px;
    color: var(--color-text-1);
    background: var(--color-bg-1);
  }

  .stock-popover-text {
    color: rgb(var(--primary-6));
  }
</style>
