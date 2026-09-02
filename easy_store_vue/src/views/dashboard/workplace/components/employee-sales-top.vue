<template>
  <a-card
    v-if="isRoot"
    class="employee-sales-card"
    :header-style="{ paddingBottom: 0 }"
    :body-style="{ padding: '12px 20px 18px' }"
  >
    <template #title>
      <div class="card-title">
        <span class="card-title-mark"></span>
        <span>本月员工销售额 TOP5</span>
      </div>
    </template>
    <template #extra>
      <span class="month-label">{{ currentMonth }}</span>
    </template>

    <a-spin :loading="loading" style="width: 100%">
      <div v-if="records.length" class="chart-wrap">
        <Chart height="230px" :option="chartOption" />
      </div>
      <div v-else-if="!loading" class="empty-state">暂无销售数据</div>
    </a-spin>
  </a-card>
</template>

<script lang="ts" setup>
  import { computed, onMounted, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import type { EChartsOption } from 'echarts';
  import useChartOption from '@/hooks/chart-option';
  import { formatPrice } from '@/api/common';
  import { useUserStore } from '@/store';
  import {
    listCashierStatistics,
    CashierStatisticsRecord,
  } from '@/views/app/AppCashierStatistics/api';

  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const loading = ref(false);
  const records = ref<CashierStatisticsRecord[]>([]);
  const currentMonth = dayjs().format('YYYY-MM');
  let hasFetched = false;

  const chartData = computed(() =>
    [...records.value]
      .sort(
        (left, right) =>
          Number(right.salesAmount || 0) - Number(left.salesAmount || 0)
      )
      .slice(0, 5)
  );

  const chartNames = computed(() =>
    chartData.value
      .map((item) => item.cashierName || '未命名员工')
      .reverse()
  );

  const chartValues = computed(() =>
    chartData.value.map((item) => Number(item.salesAmount || 0))
  );

  const totalSalesAmount = computed(() =>
    chartValues.value.reduce((total, amount) => total + amount, 0)
  );

  const pieData = computed(() =>
    chartData.value.map((item, index) => ({
      name: item.cashierName || '未命名员工',
      value: chartValues.value[index],
    }))
  );

  const { chartOption } = useChartOption((isDark): EChartsOption => ({
    color: ['#2f8cff', '#313bb2', '#21c1ed', '#32bf8a', '#ffb65a'],
    tooltip: {
      trigger: 'item',
      className: 'echarts-tooltip-diy',
      formatter(params: any) {
        return `<div><p class="tooltip-title">${params?.name || ''}</p>
          <div class="content-panel"><span>销售金额</span>
          <span class="tooltip-value">￥${formatPrice(
            Number(params?.value || 0)
          )}</span></div>
          <div class="content-panel"><span>占比</span>
          <span class="tooltip-value">${Number(params?.percent || 0).toFixed(
            1
          )}%</span></div></div>`;
      },
    },
    series: [
      {
        type: 'pie',
        radius: '68%',
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        data: pieData.value,
        itemStyle: {
          borderColor: isDark ? '#232324' : '#fff',
          borderWidth: 1,
        },
        label: {
          show: true,
          color: isDark ? 'rgba(255, 255, 255, 0.72)' : '#4e5969',
          formatter(params: any) {
            return `${params.name}: ${Number(params.percent || 0).toFixed(0)}%`;
          },
        },
        labelLine: {
          show: true,
          length: 12,
          length2: 10,
          lineStyle: {
            color: isDark ? 'rgba(255, 255, 255, 0.5)' : '#86909c',
          },
        },
        emphasis: {
          scale: true,
          scaleSize: 4,
          label: {
            show: true,
            fontWeight: 600,
          },
        },
      },
    ],
  }));

  const fetchData = async () => {
    if (!isRoot.value || hasFetched) return;
    hasFetched = true;
    loading.value = true;
    try {
      const { data } = await listCashierStatistics({
        startDate: dayjs().startOf('month').format('YYYY-MM-DD'),
        endDate: dayjs().endOf('month').format('YYYY-MM-DD'),
        current: 1,
        pageSize: 100,
      });
      records.value = data?.records || [];
    } catch {
      records.value = [];
    } finally {
      loading.value = false;
    }
  };

  watch(isRoot, fetchData, { immediate: true });
  onMounted(fetchData);
</script>

<style scoped lang="less">
  .employee-sales-card {

    margin: 16px 0;
    border: 0;
    border-radius: 8px;
    background: var(--color-bg-2);
    box-shadow: 0 4px 16px rgb(31 35 41 / 8%);
  }

  .card-title {
    display: flex;
    align-items: center;
    color: var(--color-text-1);
    font-size: 16px;
    font-weight: 600;
    line-height: 20px;
  }

  .card-title-mark {
    display: inline-block;
    width: 4px;
    height: 18px;
    margin-right: 10px;
    border-radius: 2px;
    background: rgb(var(--arcoblue-6));
  }

  .month-label {
    color: var(--color-text-3);
    font-size: 12px;
  }

  .chart-wrap {
    width: 100%;
  }

  .empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 230px;
    color: var(--color-text-3);
    font-size: 13px;
  }
</style>
