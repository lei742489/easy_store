<template>
  <a-modal
    v-model:visible="visible"
    title="收支项目"
    width="960px"
    :footer="false"
    :mask-closable="false"
  >
    <div class="toolbar">
      <a-space>
        <a-button type="primary" @click="openEditor()">
          <template #icon><icon-plus /></template>
          新增项目
        </a-button>
        <a-button @click="fetchItems">
          <template #icon><icon-refresh /></template>
          刷新
        </a-button>
      </a-space>
      <a-radio-group v-model="activeType" type="button">
        <a-radio value="all">全部</a-radio>
        <a-radio value="income">收入</a-radio>
        <a-radio value="expense">支出</a-radio>
      </a-radio-group>
    </div>
    <a-table
      row-key="id"
      :loading="loading"
      :pagination="false"
      :columns="columns"
      :data="items"
      :bordered="{ cell: true }"
      :scroll="{ y: 430 }"
    >
      <template #index="{ rowIndex }">
        {{ rowIndex + 1 }}
      </template>
      <template #itemType="{ record }">
        <a-tag :color="record.itemType === 'income' ? 'green' : 'red'">
          {{ record.itemType === 'income' ? '收入' : '支出' }}
        </a-tag>
      </template>
      <template #performance="{ record }">
        <a-checkbox :model-value="record.participatePerformance" disabled />
      </template>
      <template #disabled="{ record }">
        <a-checkbox :model-value="record.disabled" disabled />
      </template>
      <template #operations="{ record }">
        <a-button type="text" size="small" @click="openEditor(record)">
          编辑
        </a-button>
        <a-divider direction="vertical" style="margin: 0" />
        <a-popconfirm content="确认删除该收支项目？" @ok="removeItem(record)">
          <a-button type="text" size="small" status="danger">删除</a-button>
        </a-popconfirm>
      </template>
    </a-table>

    <a-modal
      v-model:visible="editorVisible"
      :title="editor.id ? '编辑收支项目' : '新增收支项目'"
      :ok-loading="saving"
      :mask-closable="false"
      @ok="saveItem"
      @cancel="resetEditor"
    >
      <a-form ref="editorFormRef" :model="editor" auto-label-width>
        <a-form-item
          field="name"
          label="项目名称"
          :rules="[{ required: true, message: '请输入项目名称' }]"
        >
          <a-input v-model="editor.name" :max-length="100" />
        </a-form-item>
        <a-form-item
          field="itemType"
          label="项目类别"
          :rules="[{ required: true, message: '请选择项目类别' }]"
        >
          <a-radio-group v-model="editor.itemType">
            <a-radio value="income">收入</a-radio>
            <a-radio value="expense">支出</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item field="participatePerformance" label="参与经营业绩统计">
          <a-switch v-model="editor.participatePerformance" />
        </a-form-item>
        <a-form-item field="disabled" label="停用">
          <a-switch v-model="editor.disabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
  import {
    addIncomeExpenseItem,
    editIncomeExpenseItem,
    IncomeExpenseItem,
    listIncomeExpenseItems,
    removeIncomeExpenseItem,
  } from '../api';

  const emit = defineEmits<{
    (e: 'changed'): void;
  }>();

  const visible = ref(false);
  const loading = ref(false);
  const activeType = ref<'all' | 'income' | 'expense'>('all');
  const items = ref<IncomeExpenseItem[]>([]);
  const editorVisible = ref(false);
  const editorFormRef = ref();
  const saving = ref(false);
  const defaultEditor = (): IncomeExpenseItem => ({
    id: undefined,
    name: '',
    itemType: activeType.value === 'all' ? 'income' : activeType.value,
    participatePerformance: true,
    disabled: false,
  });
  const editor = reactive<IncomeExpenseItem>(defaultEditor());

  const columns = computed<TableColumnData[]>(() => [
    {
      title: '行号',
      dataIndex: 'index',
      slotName: 'index',
      width: 80,
      align: 'center',
    },
    { title: '项目名称', dataIndex: 'name', minWidth: 240 },
    {
      title: '项目类别',
      dataIndex: 'itemType',
      slotName: 'itemType',
      width: 130,
      align: 'center',
    },
    {
      title: '是否参与经营业绩报告统计',
      dataIndex: 'performance',
      slotName: 'performance',
      width: 220,
      align: 'center',
    },
    {
      title: '停用',
      dataIndex: 'disabled',
      slotName: 'disabled',
      width: 90,
      align: 'center',
    },
    {
      title: '操作',
      dataIndex: 'operations',
      slotName: 'operations',
      width: 135,
      align: 'center',
    },
  ]);

  const fetchItems = async () => {
    loading.value = true;
    try {
      const { data } = await listIncomeExpenseItems({
        itemType: activeType.value === 'all' ? undefined : activeType.value,
      });
      items.value = (data || []).map((item) => ({
        ...item,
        participatePerformance: Boolean(item.participatePerformance),
        disabled: Boolean(item.disabled),
      }));
    } finally {
      loading.value = false;
    }
  };

  const resetEditor = () => {
    Object.assign(editor, defaultEditor());
  };
  const openEditor = (item?: IncomeExpenseItem) => {
    resetEditor();
    if (item) Object.assign(editor, item);
    editorVisible.value = true;
  };
  const saveItem = async () => {
    const errors = await editorFormRef.value.validate();
    if (errors) return;
    saving.value = true;
    try {
      if (editor.id) {
        await editIncomeExpenseItem({ ...editor });
      } else {
        await addIncomeExpenseItem({ ...editor });
      }
      Message.success('保存成功');
      editorVisible.value = false;
      resetEditor();
      await fetchItems();
      emit('changed');
    } finally {
      saving.value = false;
    }
  };
  const removeItem = async (item: IncomeExpenseItem) => {
    if (!item.id) return;
    await removeIncomeExpenseItem(item.id);
    Message.success('删除成功');
    await fetchItems();
    emit('changed');
  };
  const showModal = async () => {
    visible.value = true;
    await fetchItems();
  };

  watch(activeType, fetchItems);

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }
</style>
