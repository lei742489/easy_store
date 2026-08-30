<template>
  <a-spin :loading="loading" style="width: 100%">
    <div class="statistics-list">
      <div v-for="item in visibleItems" :key="item.key" class="statistics-card">
        <div class="statistics-icon" :class="`icon-${item.key}`">
          <component :is="item.icon" />
        </div>
        <div class="statistics-content">
          <div class="statistics-title">{{ item.title }}</div>
          <div class="statistics-value">
            {{
              item.money
                ? `¥${formatPrice(item.value)}`
                : formatNumber(item.value)
            }}
          </div>
          <div class="statistics-caption">
            <span>{{ item.unit }}</span>
            <span v-if="item.compare" class="statistics-compare">
              <span>较昨日</span>
              <icon-caret-up
                v-if="item.compare.direction === 'up'"
                class="compare-arrow compare-up"
              />
              <icon-caret-down
                v-else-if="item.compare.direction === 'down'"
                class="compare-arrow compare-down"
              />
              <span
                v-if="item.compare.percent === null"
                class="compare-percent compare-neutral"
              >
                --
              </span>
              <span
                v-else
                class="compare-percent"
                :class="`compare-${item.compare.direction}`"
              >
                {{ item.compare.percent.toFixed(1) }}%
              </span>
            </span>
          </div>
        </div>
      </div>
    </div>
  </a-spin>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import { useUserStore } from '@/store';
  import { formatPrice } from '@/api/common';
  import { getTodayHomeStatistics, HomeStatistics } from '../api';

  interface StatisticsItem {
    key: string;
    title: string;
    value: number;
    unit: string;
    money?: boolean;
    icon: string;
    compare?: {
      direction: 'up' | 'down' | 'same';
      percent: number | null;
    };
  }

  const loading = ref(false);
  const statistics = ref<HomeStatistics>({});
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);

  const getComparison = (value: number, yesterdayValue: number) => {
    if (Math.abs(yesterdayValue) < 0.000001) {
      return {
        direction: value > 0 ? 'up' : 'same',
        percent: null,
      };
    }
    const change = ((value - yesterdayValue) / Math.abs(yesterdayValue)) * 100;
    return {
      direction:
        change > 0.000001 ? 'up' : change < -0.000001 ? 'down' : 'same',
      percent: Math.abs(change),
    };
  };

  const visibleItems = computed<StatisticsItem[]>(() => {
    const items: StatisticsItem[] = [
      {
        key: 'sales',
        title: '今日销售额',
        value: Number(statistics.value.salesAmount || 0),
        unit: '今日销售金额',
        money: true,
        icon: 'icon-storage',
        compare: getComparison(
          Number(statistics.value.salesAmount || 0),
          Number(statistics.value.salesAmountYesterday || 0)
        ),
      },
      {
        key: 'purchase',
        title: '今日采购额',
        value: Number(statistics.value.purchaseAmount || 0),
        unit: '今日进货金额',
        money: true,
        icon: 'icon-archive',
        compare: getComparison(
          Number(statistics.value.purchaseAmount || 0),
          Number(statistics.value.purchaseAmountYesterday || 0)
        ),
      },
      {
        key: 'stock',
        title: '库存总数',
        value: Number(statistics.value.stockTotal || 0),
        unit: '当前库存数量',
        icon: 'icon-storage',
      },
    ];
    if (isRoot.value) {
      items.splice(1, 0, {
        key: 'profit',
        title: '今日利润',
        value: Number(statistics.value.profitAmount || 0),
        unit: '今日销售毛利',
        money: true,
        icon: 'icon-apps',
        compare: getComparison(
          Number(statistics.value.profitAmount || 0),
          Number(statistics.value.profitAmountYesterday || 0)
        ),
      });
    }
    return items;
  });

  const formatNumber = (value: number) =>
    new Intl.NumberFormat('zh-CN', {
      maximumFractionDigits: 2,
    }).format(value);

  const fetchStatistics = async () => {
    loading.value = true;
    try {
      const { data } = await getTodayHomeStatistics();
      statistics.value = data || {};
    } finally {
      loading.value = false;
    }
  };

  fetchStatistics();
</script>

<style scoped lang="less">
  .statistics-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .statistics-card {
    display: flex;
    align-items: center;
    min-height: 92px;
    padding: 16px;
    border: 1px solid rgb(var(--gray-2));
    border-radius: 8px;
    background: var(--color-bg-2);
    box-shadow: 0 4px 14px rgb(31 35 41 / 6%);
  }

  .statistics-icon {
    display: flex;
    flex: 0 0 48px;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    margin-right: 14px;
    border-radius: 10px;
    font-size: 24px;
  }

  .icon-sales {
    color: rgb(var(--arcoblue-6));
    background: rgb(var(--arcoblue-1));
  }

  .icon-profit {
    color: rgb(var(--green-6));
    background: rgb(var(--green-1));
  }

  .icon-purchase {
    color: rgb(var(--orange-6));
    background: rgb(var(--orange-1));
  }

  .icon-stock {
    color: rgb(var(--purple-6));
    background: rgb(var(--purple-1));
  }

  .statistics-content {
    min-width: 0;
  }

  .statistics-title {
    color: var(--color-text-3);
    font-size: 12px;
    line-height: 18px;
  }

  .statistics-value {
    overflow: hidden;
    color: var(--color-text-1);
    font-size: 20px;
    font-weight: 600;
    line-height: 28px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .statistics-caption {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--color-text-3);
    font-size: 11px;
    line-height: 16px;
  }

  .statistics-compare {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    white-space: nowrap;
  }

  .compare-arrow {
    font-size: 11px;
  }

  .compare-percent.compare-up,
  .compare-arrow.compare-up {
    color: rgb(var(--red-6));
  }

  .compare-percent.compare-down,
  .compare-arrow.compare-down {
    color: rgb(var(--green-6));
  }

  .compare-percent.compare-same,
  .compare-neutral {
    color: var(--color-text-3);
  }
</style>
