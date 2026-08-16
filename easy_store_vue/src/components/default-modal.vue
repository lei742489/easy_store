<template>
  <a-modal v-model:visible="visible" @ok="handleOk" @cancel="handleCancel">
    <template #title> {{ modalTitle }} </template>
    <div>
      {{ modalContent }}
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
  import { ref } from 'vue';

  const visible = ref(false);
  const modalContent = ref('');
  const modalTitle = ref('提示');
  const idx = ref<any>();

  // 定义 emit
  const emit = defineEmits<{
    (e: 'ok', data: -1): void;
  }>();

  function openModal(title?: string, content?: string, i?: any) {
    modalTitle.value = title || '提示';
    modalContent.value = content || '';
    idx.value = i;
    visible.value = true;
  }

  function closeModal() {
    visible.value = false;
  }

  function handleOk() {
    emit('ok', idx.value);
    closeModal();
  }

  function handleCancel() {
    emit('ok', -1);
    closeModal();
  }

  // ✅ 暴露给父组件
  defineExpose({
    openModal,
    closeModal,
  });
</script>
