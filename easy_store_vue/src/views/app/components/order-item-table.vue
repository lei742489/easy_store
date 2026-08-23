<template>
  <div ref="itemFormRef" class="item-form">
    <a-table
      ref="goodsTableRef"
      :columns="columns"
      :data="data"
      :pagination="false"
      size="small"
      :scroll="{ x: '100%', y: 258 }"
      :scrollbar="true"
      :summary="true"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>

      <template #goodsId="{ rowIndex }">
        <div
          v-if="props.goodsSearchPanel"
          :data-goods-row="rowIndex"
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
              placeholder=""
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
            placeholder=""
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
          placeholder=""
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
          :min="1"
          @change="updateTotalAmount(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].quantity"
          style="color: red"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 1)"
        ></a-input-number>
      </template>

      <template #unitPrice="{ rowIndex }">
        <a-input-number
          v-model="data[rowIndex].unitPrice"
          :precision="2"
          :min="0"
          @change="updateTotalAmount(data[rowIndex])"
        ></a-input-number>
      </template>

      <template #totalAmount="{ rowIndex }">
        <a-input-number
          v-if="orderType == 1"
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          :min="0"
          @change="updateUnitPrice(data[rowIndex])"
        ></a-input-number>

        <a-input-number
          v-else
          v-model="data[rowIndex].totalAmount"
          :precision="2"
          @change="(val:number | undefined) => handleNegativeChange(val, rowIndex, 2)"
        ></a-input-number>
      </template>

      <template #note="{ rowIndex }">
        <a-input v-model="data[rowIndex].note"></a-input>
      </template>

      <template #operations="{ rowIndex }">
        <a-popconfirm content="确认删除该条数据?" @ok="handelRemove(rowIndex)">
          <a-button type="text" size="small">删除</a-button>
        </a-popconfirm>
      </template>

      <template #summary-cell="{ column, record }">
        <div v-if="column.dataIndex === 'index'">
          <a-button
            type="text"
            size="medium"
            style="padding: 0"
            @click="addItem(1)"
            >增加一行</a-button
          >
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

        <div v-if="column.dataIndex == 'operations'">
          <a-popconfirm content="清空所有数据?" @ok="clearAll">
            <a-button type="text" size="small">清空</a-button>
          </a-popconfirm>
        </div>
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
            <div class="goods-name">{{ goods.value || goods.label || '' }}</div>
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
  import { ref, nextTick, watch, onBeforeUnmount, onMounted } from 'vue';
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
    }>(),
    {
      goodsSearchPanel: false,
      goodsSearchType: 'sale',
    }
  );

  export interface OrderItemRow {
    id?: number;
    goodsId?: number | string;
    goodsId_dictText?: string;
    goodsName?: string;
    categoryId?: number;
    categoryId_dictText?: string;
    unit?: string;
    quantity?: number;
    unitPrice?: number;
    stock?: number;
    panelSelectedGoods?: boolean;
    totalAmount?: number;
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
    note: undefined,
  };
  const goodsPageNo = ref(1);
  const itemFormRef = ref<HTMLElement | null>(null);
  const activeGoodsRowIndex = ref<number | undefined>();
  const goodsPanelVisible = ref(false);
  const goodsPanelTop = ref(0);
  const goodsPanelLeft = ref(0);
  const goodsPanelWidth = ref(0);
  const highlightedGoodsIndex = ref(0);
  const hideZeroStock = ref(true);
  const lastSearchToken = ref(0);
  const isPurchaseGoodsSearch = props.goodsSearchType === 'purchase';
  const showZeroStockFilter = props.goodsSearchType === 'sale';

  const columns: TableColumnData[] = [
    {
      title: '序号',
      dataIndex: 'index',
      slotName: 'index',
      align: 'center',
      width: 70,
    },
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
      width: 120,
    },
    {
      title: '货品类别',
      dataIndex: 'categoryId',
      align: 'center',
      slotName: 'category',
      width: 200,
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      align: 'center',
      slotName: 'quantity',
      width: 140,
    },
    {
      title: '单价',
      dataIndex: 'unitPrice',
      align: 'center',
      slotName: 'unitPrice',
      width: 200,
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      align: 'center',
      slotName: 'totalAmount',
      width: 200,
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
    },
  ];

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
    goodsSearchData.value = [];
    goodsPageNo.value = 1;
  };

  const handleSearchKey = async (key: any) => {
    if (!key) {
      clearSearchData();
      return;
    }
    goodsPageNo.value = 1;
    const res = await searchKey(key, 1);
    goodsSearchData.value = res.data;
  };

  const updateGoodsPanelPosition = (rowIndex: number) => {
    nextTick(() => {
      const root = itemFormRef.value;
      const inputWrap = root?.querySelector(
        `[data-goods-row="${rowIndex}"]`
      ) as HTMLElement | null;
      if (!root || !inputWrap) return;
      const rootRect = root.getBoundingClientRect();
      const inputRect = inputWrap.getBoundingClientRect();
      const preferredWidth =
        props.goodsSearchType === 'purchase'
          ? inputRect.width
          : inputRect.width * 1.3;
      const width = Math.min(preferredWidth, rootRect.width);
      const left = Math.max(
        0,
        Math.min(inputRect.left - rootRect.left, rootRect.width - width)
      );
      goodsPanelTop.value = inputRect.bottom - rootRect.top + 2;
      goodsPanelLeft.value = left;
      goodsPanelWidth.value = width;
    });
  };

  const queryPanelGoods = async (key: string) => {
    const token = lastSearchToken.value + 1;
    lastSearchToken.value = token;
    goodsPageNo.value = 1;
    const res = await searchKey(
      key,
      1,
      showZeroStockFilter ? hideZeroStock.value : undefined
    );
    if (token !== lastSearchToken.value) return;
    goodsSearchData.value = res.data;
    highlightedGoodsIndex.value = 0;
    goodsPanelVisible.value = true;
  };

  const handleGoodsInputFocus = (rowIndex: number) => {
    activeGoodsRowIndex.value = rowIndex;
    updateGoodsPanelPosition(rowIndex);
    const key = data.value[rowIndex].goodsId_dictText || '';
    if (key.trim() && goodsSearchData.value.length > 0) {
      goodsPanelVisible.value = true;
    }
  };

  const handlePanelSearch = async (rowIndex: number, value: string) => {
    activeGoodsRowIndex.value = rowIndex;
    updateGoodsPanelPosition(rowIndex);
    data.value[rowIndex].goodsId_dictText = value;
    data.value[rowIndex].goodsId = undefined;
    data.value[rowIndex].goodsName = value;
    data.value[rowIndex].stock = undefined;
    data.value[rowIndex].panelSelectedGoods = false;
    if (!value || !value.trim()) {
      clearSearchData();
      goodsPanelVisible.value = false;
      return;
    }
    await queryPanelGoods(value);
  };

  const refreshGoodsPanel = async () => {
    const rowIndex = activeGoodsRowIndex.value;
    if (rowIndex === undefined) return;
    const key = data.value[rowIndex].goodsId_dictText || '';
    if (!key.trim()) return;
    await queryPanelGoods(key);
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

  const handleDocumentMouseDown = (event: MouseEvent) => {
    const root = itemFormRef.value;
    const target = event.target as Node;
    const panel = root?.querySelector('.goods-search-panel');
    const inputWrap = root?.querySelector(
      `[data-goods-row="${activeGoodsRowIndex.value}"]`
    );
    if (!root || panel?.contains(target) || inputWrap?.contains(target)) return;
    hideGoodsPanel();
  };

  const initItemList = (size: number) => {
    if (size <= 0) {
      const initNum = 10;
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
    const res = await searchKey('', 1);
    goodsSearchData.value = res.data;
  };

  const loadGoodsMore = async (goodsItem: OrderItemRow) => {
    goodsPageNo.value += 1;
    const res = await searchKey(
      goodsItem.goodsId_dictText || '',
      goodsPageNo.value
    );
    if (res.data.length > 0) {
      goodsSearchData.value = goodsSearchData.value.concat(res.data);
    }
  };

  function emitChange() {
    let tList = 0;
    data.value.forEach((item) => {
      tList = addPrice(
        tList,
        mulPrice(item.quantity || 0, item.unitPrice || 0)
      );
    });
    emit('change', tList);
  }

  function updateTotalAmount(goodsItem: OrderItemRow) {
    goodsItem.totalAmount = mulPrice(
      goodsItem.quantity || 0,
      goodsItem.unitPrice || 0
    );
    emitChange();
  }

  const confirmGoods = (goods: GoodsSearchResult) => {
    const rowIndex = activeGoodsRowIndex.value;
    if (rowIndex === undefined || !goods) return;
    const row = data.value[rowIndex];
    if (!row) return;
    const goodsName = goods.value || goods.label || '';
    row.goodsId = goods.goodsId;
    row.goodsId_dictText = goodsName;
    row.goodsName = goodsName;
    row.unit = goods.unit;
    row.categoryId = goods.categoryId;
    row.categoryId_dictText = goods.categoryName;
    row.stock = goods.stock;
    row.panelSelectedGoods = true;
    row.quantity = orderType.value === 1 ? 1 : -1;
    row.unitPrice = getPanelUnitPrice(goods);
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

  const updateUnitPrice = (goodsItem: OrderItemRow) => {
    goodsItem.unitPrice = divPrice(
      goodsItem.totalAmount || 0,
      goodsItem.quantity || 0
    );
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
      goodsItem.goodsId_dictText = goodsName || option.label || '';
      goodsItem.goodsName = goodsItem.goodsId_dictText;
      goodsItem.unit = unit;
      goodsItem.categoryId = categoryId;
      goodsItem.quantity = orderType.value === 1 ? 1 : -1;
      goodsItem.categoryId_dictText = categoryName;
      goodsItem.stock = stock;
      goodsItem.unitPrice = undefined;
      goodsItem.totalAmount = undefined;
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

        updateUnitPrice(item);
      }
    });
  });

  onMounted(() => {
    document.addEventListener('mousedown', handleDocumentMouseDown);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleDocumentMouseDown);
  });

  defineExpose({ getItemsList, initData, clearAll });
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
  }

  .goods-search-panel {
    position: absolute;
    z-index: 20;
    border: 1px solid var(--color-border-3);
    background: var(--color-bg-popup);
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

  .price {
    text-align: right;
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
    background: var(--color-bg-1);
  }

  .stock-popover-text {
    color: rgb(var(--primary-6));
  }
</style>
