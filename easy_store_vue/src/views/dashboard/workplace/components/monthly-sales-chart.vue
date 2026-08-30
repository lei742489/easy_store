<template>
  <a-card
    class="monthly-sales-card"
    :header-style="{ paddingBottom: 0 }"
    :body-style="{ padding: '12px 20px 18px' }"
  >
    <template #title>
      <div class="chart-title">
        <span class="chart-title-mark"></span>
        <span>月销售统计</span>
        <a-month-picker
          style="margin-left: 20px; margin-bottom: 4px"
          v-model="selectedMonth"
          class="month-picker"
          value-format="YYYY-MM"
          :allow-clear="false"
          @change="handleMonthChange"
        />
      </div>
    </template>
    <template #extra>
      <span class="chart-range">{{ dateRangeText }}</span>
    </template>
    <a-spin :loading="loading" style="width: 100%">
      <Chart height="300px" :option="chartOption" />
      <div class="summary-row">
        <div class="summary-item">
          <span class="summary-label">销售金额：</span>
          <span class="summary-value sales-value">
            ¥{{ formatPrice(totalSalesAmount) }}
          </span>
        </div>
        <div v-if="isRoot" class="summary-item">
          <span class="summary-label">毛利：</span>
          <span class="summary-value profit-value">
            ¥{{ formatPrice(totalProfitAmount) }}
          </span>
        </div>
      </div>
    </a-spin>
  </a-card>
</template>

<script lang="ts" setup>
  import { computed, ref } from 'vue';
  import dayjs from 'dayjs';
  import { graphic } from 'echarts';
  import { useUserStore } from '@/store';
  import useChartOption from '@/hooks/chart-option';
  import { formatPrice } from '@/api/common';
  import {
    listMonthlySaleStatistics,
    MonthlySaleStatisticsRecord,
  } from '@/views/app/AppSaleStatistics/api';

  const loading = ref(false);
  const records = ref<MonthlySaleStatisticsRecord[]>([]);
  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const selectedMonth = ref(dayjs().format('YYYY-MM'));

  const selectedMonthDate = computed(() => dayjs(`${selectedMonth.value}-01`));
  const startDate = computed(() =>
    selectedMonthDate.value.startOf('month').format('YYYY-MM-DD')
  );
  const endDate = computed(() =>
    selectedMonthDate.value.isSame(dayjs(), 'month')
      ? dayjs().format('YYYY-MM-DD')
      : selectedMonthDate.value.endOf('month').format('YYYY-MM-DD')
  );
  const dateRangeText = computed(
    () => `${startDate.value} 至 ${endDate.value}`
  );
  const xAxisData = computed(() =>
    records.value.map((item) => item.date || '')
  );
  const salesData = computed(() =>
    records.value.map((item) => Number(item.salesAmount || 0))
  );
  const profitData = computed(() =>
    records.value.map((item) => Number(item.profitAmount || 0))
  );
  const totalSalesAmount = computed(() =>
    salesData.value.reduce((total, amount) => total + amount, 0)
  );
  const totalProfitAmount = computed(() =>
    profitData.value.reduce((total, amount) => total + amount, 0)
  );

  const { chartOption } = useChartOption((isDark) => ({
    grid: {
      left: 12,
      right: 16,
      top: 38,
      bottom: 26,
      containLabel: true,
    },
    legend: {
      top: 0,
      left: 'center',
      icon: 'roundRect',
      itemWidth: 16,
      itemHeight: 6,
      textStyle: {
        color: isDark ? 'rgba(255, 255, 255, 0.7)' : '#4e5969',
      },
      data: isRoot.value ? ['销售金额', '毛利'] : ['销售金额'],
    },
    tooltip: {
      trigger: 'axis',
      className: 'echarts-tooltip-diy',
      formatter(params: any) {
        const items = Array.isArray(params) ? params : [params];
        const date = items[0]?.axisValueLabel || '';
        const lines = items
          .map(
            (item: any) =>
              `<div class="content-panel"><span>${
                item.seriesName
              }</span><span class="tooltip-value">¥${formatPrice(
                Number(item.value || 0)
              )}</span></div>`
          )
          .join('');
        return `<div><p class="tooltip-title">${date}</p>${lines}</div>`;
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxisData.value,
      axisLine: {
        lineStyle: {
          color: isDark ? '#4e5969' : '#e5e8ef',
        },
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: isDark ? 'rgba(255, 255, 255, 0.7)' : '#86909c',
        formatter(value: string, index: number) {
          if (index === 0 || index === xAxisData.value.length - 1) {
            return value.slice(5);
          }
          return index % 5 === 0 ? value.slice(5) : '';
        },
      },
      splitLine: {
        show: true,
        lineStyle: {
          color: isDark ? '#3c3c3c' : '#eef1f5',
        },
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: isDark ? 'rgba(255, 255, 255, 0.7)' : '#86909c',
        formatter(value: number) {
          return value >= 10000 ? `${(value / 10000).toFixed(0)}万` : value;
        },
      },
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: isDark ? '#3c3c3c' : '#e5e8ef',
        },
      },
    },
    series: [
      {
        name: '销售金额',
        type: 'line',
        data: salesData.value,
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
          color: new graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#22c7f2' },
            { offset: 1, color: '#4d6bff' },
          ]),
        },
        itemStyle: {
          color: '#4d6bff',
        },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(77, 107, 255, 0.18)' },
            { offset: 1, color: 'rgba(77, 107, 255, 0)' },
          ]),
        },
      },
      ...(isRoot.value
        ? [
            {
              name: '毛利',
              type: 'line',
              data: profitData.value,
              smooth: true,
              showSymbol: false,
              symbol: 'circle',
              symbolSize: 8,
              lineStyle: {
                width: 2,
                color: '#32bf8a',
              },
              itemStyle: {
                color: '#32bf8a',
              },
            },
          ]
        : []),
    ],
  }));

  const handleMonthChange = () => {
    fetchData();
  };

  const fetchData = async () => {
    loading.value = true;
    try {
      const { data } = await listMonthlySaleStatistics({
        startDate: startDate.value,
        endDate: endDate.value,
      });
      records.value = data || [];
    } finally {
      loading.value = false;
    }
  };

  fetchData();
</script>

<style scoped lang="less">
  .monthly-sales-card {
    margin-top: 16px;
    border: 0;
    border-radius: 8px;
    background: var(--color-bg-2);
    box-shadow: 0 4px 16px rgb(31 35 41 / 8%);
  }

  .chart-title {
    display: flex;
    align-items: center;
    color: var(--color-text-1);
    font-size: 16px;
    font-weight: 600;
    line-height: 20px;
  }

  .month-picker {
    width: 120px;
    margin-left: 22px;
    font-size: 12px;
    font-weight: 400;
  }

  .chart-title-mark {
    display: inline-block;
    width: 4px;
    height: 18px;
    margin-right: 10px;
    border-radius: 2px;
    background: rgb(var(--arcoblue-6));
  }

  .chart-range {
    color: var(--color-text-3);
    font-size: 12px;
  }

  .summary-row {
    display: flex;
    align-items: center;
    gap: 32px;
    min-height: 36px;
    margin-top: 4px;
    padding: 12px 8px 0;
    border-top: 1px solid var(--color-border-2);
  }

  .summary-item {
    display: flex;
    align-items: baseline;
    min-width: 0;
  }

  .summary-label {
    color: var(--color-text-2);
    font-size: 13px;
  }

  .summary-value {
    font-size: 16px;
    font-weight: 600;
  }

  .sales-value {
    color: rgb(var(--arcoblue-6));
  }

  .profit-value {
    color: rgb(var(--green-6));
  }

  @media (max-width: 600px) {
    .summary-row {
      gap: 16px;
      padding-left: 0;
    }

    .summary-value {
      font-size: 14px;
    }
  }
</style>
