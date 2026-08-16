<template>
  <div class="time-select">
    <a-select
      v-model="timeIdx"
      placeholder="请选择"
      :options="timeMenu"
      style="width: 95px"
    ></a-select>
    <a-range-picker v-model="timeSelect" @change="onChange" />
  </div>
</template>

<script lang="ts" setup>
  import dayjs from 'dayjs';

  import { onMounted, reactive, ref, watch } from 'vue';

  interface MenuItem {
    value: number;
    label: string;
  }
  const props = defineProps<{
    defaultTimeIdx?: number;
  }>();

  const timeIdx = ref<number>();

  const timeSelect = ref<string[]>([]);
  const timeMenu = reactive<MenuItem[]>([
    {
      value: 1,
      label: '今日',
    },
    {
      value: 2,
      label: '本月',
    },
    {
      value: 3,
      label: '本年',
    },
    {
      value: 4,
      label: '自定义',
    },
  ]);

  const emit = defineEmits<{
    (e: 'change', data: string[]): void;
  }>();

  const onChange = (e: string[]) => {
    emit('change', e);
    timeIdx.value = 4;
  };

  const clear = () => {
    timeSelect.value = [];
    timeIdx.value = undefined;
  };

  const applyPreset = (newVal?: number) => {
    if (!newVal || newVal === 4) return;
    let begin = '';
    let end = '';
    if (newVal === 1) {
      // 今日
      begin = dayjs().format('YYYY-MM-DD');
      end = dayjs().format('YYYY-MM-DD');
    } else if (newVal === 2) {
      // 本月
      begin = dayjs().startOf('month').format('YYYY-MM-DD');
      end = dayjs().endOf('month').format('YYYY-MM-DD');
    } else if (newVal === 3) {
      // 本年
      begin = dayjs().startOf('year').format('YYYY-MM-DD');
      end = dayjs().endOf('year').format('YYYY-MM-DD');
    }

    timeSelect.value = [begin, end];
    emit('change', timeSelect.value);
  };

  const setPreset = (value: number) => {
    timeIdx.value = value;
  };

  watch(timeIdx, applyPreset);

  onMounted(() => {
    if (props.defaultTimeIdx) {
      setPreset(props.defaultTimeIdx);
    }
  });

  defineExpose({ clear, setPreset });
</script>

<style lang="less" scoped>
  .time-select {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }
</style>
