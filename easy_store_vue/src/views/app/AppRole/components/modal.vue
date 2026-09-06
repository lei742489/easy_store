<template>
  <a-modal
    :visible="visible"
    unmount-on-close
    :mask-closable="true"
    :ok-loading="loading"
    width="760px"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <template #title>{{ title }}</template>
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        field="name"
        label="角色名称"
        :rules="[{ required: true, message: '请输入角色名称' }]"
      >
        <a-input v-model="form.name" placeholder="请输入角色名称" />
      </a-form-item>

      <a-form-item field="status" label="状态">
        <a-radio-group v-model="form.status" type="button">
          <a-radio :value="1">启用</a-radio>
          <a-radio :value="0">禁用</a-radio>
        </a-radio-group>
      </a-form-item>

      <a-form-item field="remarks" label="备注">
        <a-textarea
          v-model="form.remarks"
          placeholder="请输入备注"
          :auto-size="{ minRows: 2, maxRows: 4 }"
        />
      </a-form-item>

      <a-form-item field="menuIds" label="角色权限配置">
        <a-spin :loading="menuLoading" style="width: 100%">
          <a-tree
            v-model:checked-keys="checkedKeys"
            class="permission-tree"
            :data="permissionTree"
            checkable
            default-expand-all
          />
        </a-spin>
      </a-form-item>

      <a-form-item field="dataViewPermissionCodes" label="数据查看权限">
        <a-checkbox-group v-model="dataViewPermissionCodes">
          <a-space wrap>
            <a-checkbox
              v-for="item in dataViewPermissions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </a-checkbox>
          </a-space>
        </a-checkbox-group>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import type { AppPermissionTreeNode, AppRole } from '../types/AppRole';
  import {
    add,
    edit,
    listAllMenus,
    menuIds,
    permissionCodes,
  } from '../api/api-AppRole';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const loading = ref(false);
  const menuLoading = ref(false);
  const permissionTree = ref<AppPermissionTreeNode[]>([]);
  const checkedKeys = ref<string[]>([]);
  const dataViewPermissionCodes = ref<string[]>([]);
  const dataViewPermissions = [
    { label: '成本价', value: 'data_view:cost_price' },
    { label: '进货价', value: 'data_view:purchase_price' },
    { label: '批发价', value: 'data_view:trade_price' },
    { label: '销售价', value: 'data_view:sale_price' },
  ];
  const dataViewPermissionCodeSet = new Set(
    dataViewPermissions.map((item) => item.value)
  );

  const defaultForm = (): AppRole => ({
    id: undefined,
    name: undefined,
    remarks: undefined,
    status: 1,
    menuIds: [],
    permissionCodes: [],
  });

  const form = reactive<AppRole>(defaultForm());

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const loadMenus = async () => {
    menuLoading.value = true;
    try {
      const { data } = await listAllMenus();
      permissionTree.value = data || [];
    } finally {
      menuLoading.value = false;
    }
  };

  const showModal = async (item: AppRole) => {
    visible.value = true;
    Object.assign(form, defaultForm(), item);
    title.value = item.id ? '编辑-角色管理' : '新增-角色管理';
    await loadMenus();
    if (item.id) {
      const [{ data: roleMenuIds }, { data: rolePermissionCodes }] =
        await Promise.all([menuIds(item.id), permissionCodes(item.id)]);
      form.menuIds = roleMenuIds || [];
      form.permissionCodes = rolePermissionCodes || [];
      dataViewPermissionCodes.value = form.permissionCodes.filter((code) =>
        dataViewPermissionCodeSet.has(code)
      );
      checkedKeys.value = [
        ...form.permissionCodes.filter(
          (code) => !dataViewPermissionCodeSet.has(code)
        ),
      ];
    } else {
      checkedKeys.value = [];
      dataViewPermissionCodes.value = [];
    }
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm());
    checkedKeys.value = [];
    dataViewPermissionCodes.value = [];
  };

  function buildMenuMaps(nodes: AppPermissionTreeNode[]) {
    const menuIdMap = new Map<string, number>();
    const menuCodeMap = new Map<number, string>();
    const menuActionMap = new Map<string, string[]>();
    const walk = (treeNodes: AppPermissionTreeNode[]) => {
      treeNodes.forEach((node) => {
        if (node.menuCode && node.menuId) {
          menuIdMap.set(node.menuCode, node.menuId);
          menuCodeMap.set(node.menuId, node.menuCode);
          if (node.children?.length) {
            menuActionMap.set(
              node.menuCode,
              node.children.map((child) => child.key)
            );
          }
        }
        if (node.children) walk(node.children);
      });
    };
    walk(nodes);
    return { menuIdMap, menuCodeMap, menuActionMap };
  }

  function applyCheckedKeys() {
    const menuIdsSet = new Set<number>();
    const permissionCodesSet = new Set<string>();
    const { menuIdMap, menuCodeMap, menuActionMap } = buildMenuMaps(
      permissionTree.value
    );

    checkedKeys.value.forEach((key) => {
      if (key.startsWith('menu:')) {
        const menuId = Number(key.replace('menu:', ''));
        if (!Number.isNaN(menuId)) {
          menuIdsSet.add(menuId);
          const menuCode = menuCodeMap.get(menuId);
          if (menuCode) {
            (menuActionMap.get(menuCode) || []).forEach((permissionCode) =>
              permissionCodesSet.add(permissionCode)
            );
          }
        }
        return;
      }

      permissionCodesSet.add(key);
      const menuCode = key.split(':')[0];
      const menuId = menuIdMap.get(menuCode);
      if (menuId) menuIdsSet.add(menuId);
    });
    dataViewPermissionCodes.value.forEach((code) =>
      permissionCodesSet.add(code)
    );

    form.menuIds = Array.from(menuIdsSet);
    form.permissionCodes = Array.from(permissionCodesSet);
  }

  const handleOk = async () => {
    const errors = await formRef.value.validate();
    if (errors) return;

    loading.value = true;
    try {
      applyCheckedKeys();
      if (form.id) {
        await edit(form);
      } else {
        await add(form);
      }
      Message.success('操作成功');
      handleCancel();
      emit('ok', 1);
    } finally {
      loading.value = false;
    }
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .permission-tree {
    width: 100%;
    max-height: 380px;
    overflow: auto;
    padding: 4px 0;
  }
</style>
