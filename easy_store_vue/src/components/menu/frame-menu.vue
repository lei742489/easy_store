<template>
  <div class="frame-menu">
    <div class="f-item" @click="windowMinimize()">
      <icon-minus :size="20" class="icon" />
    </div>
    <div v-if="showMaximize" class="f-item" @click="changeWindow">
      <icon-fullscreen v-if="!isMax" :size="20" class="icon" />
      <icon-fullscreen-exit v-else :size="20" class="icon" />
    </div>
    <div class="f-item" @click="windowClose()">
      <icon-close :size="20" class="icon" />
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { ref, computed, defineProps, defineEmits } from 'vue';
  import {
    windowMinimize,
    windowClose,
    windowMaximize,
  } from '@/api/electron/electron-api';

  const props = defineProps<{ modelValue: boolean }>();

  const isMax = ref(false);
  const emit = defineEmits<{
    (e: 'update:modelValue', value: boolean): void;
  }>();
  const showMaximize = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val),
  });
  const changeWindow = () => {
    isMax.value = !isMax.value;
    windowMaximize();
  };
</script>

<style lang="less" scoped>
  .frame-menu {
    position: fixed;
    top: 0;
    right: 0;
    z-index: 999;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding-top: 4px;
    transform: scale(0.84);

    -webkit-app-region: no-drag;
    .f-item {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 30px;
      width: 30px;
      margin-right: 6px;
    }

    .f-item:hover {
      background: #5941cd;
      .icon {
        color: #fff;
      }
    }
    .f-item:nth-child(2):hover {
      background: #0984e3;
    }
    .f-item:last-child:hover {
      background: #e74c3c;
    }
  }
</style>
