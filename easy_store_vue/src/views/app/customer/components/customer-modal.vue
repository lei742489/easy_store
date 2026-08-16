<template>
  <div class="drawer">
    <a-drawer
      width="40%"
      :visible="visible"
      unmount-on-close
      :mask-closable="true"
      :ok-loading="loading"
      @ok="handleOk"
      @cancel="handleCancel"
    >
      <template #title> {{ title }} </template>
      <div>
        <a-form ref="formRef" :model="form">
          <a-form-item
            field="name"
            label="客户名称"
            :rules="[{ required: true, message: '请输入客户名称' }]"
          >
            <a-input
              v-model="form.name"
              placeholder="请输入客户名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="contactName" label="联系人">
            <a-input
              v-model="form.contactName"
              placeholder="请输入联系人"
              :max-length="50"
            />
          </a-form-item>
          <a-form-item field="mobile" label="手机">
            <a-input
              v-model="form.mobile"
              placeholder="请输入手机号"
              :max-length="50"
            />
          </a-form-item>
          <a-form-item field="phone" label="座机电话">
            <a-input
              v-model="form.phone"
              placeholder="请输入座机电话"
              :max-length="50"
            />
          </a-form-item>
          <a-form-item field="mail" label="邮 箱">
            <a-input
              v-model="form.mail"
              placeholder="请输入邮箱"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="birthday" label="生日">
            <a-date-picker v-model="form.birthday" placeholder="请选择" />
          </a-form-item>
          <a-form-item field="address" label="详细地址">
            <a-input
              v-model="form.address"
              placeholder="请输入详细地址"
              :max-length="300"
            />
          </a-form-item>

          <a-form-item field="discount" label="折扣%">
            <a-input-number
              v-model="form.discount"
              placeholder="请输折扣"
              :max-length="3"
              :min="0"
              :max="100"
            />
          </a-form-item>
          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              style="height: 90px"
              :max-length="300"
            />
          </a-form-item>
          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              style="height: 90px"
              :max-length="300"
            />
          </a-form-item>

          <a-form-item field="categoryId" label="所属分类">
            <a-tree-select
              v-model="form.categoryId"
              :data="categoryTreeData"
              placeholder="请选择分类..."
              :field-names="{ key: 'id' }"
              :allow-search="true"
              :fallback-option="categoryFallback"
            ></a-tree-select>
          </a-form-item>

          <a-form-item field="categoryId" label="客户等级">
            <div
              style="
                display: flex;
                flex-direction: row;
                width: 100%;
                align-items: center;
                justify-content: space-between;
              "
            >
              <a-select
                v-model="form.levelId"
                :options="levelList"
                :field-names="{ value: 'id', label: 'title' }"
                placeholder="请选择..."
                :fallback-option="levelFallback"
                allow-clear
              />

              <a-button
                size="small"
                type="primary"
                style="margin-left: 6px"
                @click="openPriceModal()"
                >管理</a-button
              >
            </div>
          </a-form-item>

          <a-form-item field="status" label="状态">
            <a-select v-model="form.status" placeholder="请选择 ...">
              <a-option :value="1">启用</a-option>
              <a-option :value="0">禁用</a-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>

    <level-list ref="levelListRef" @ok="fetchLevelList"></level-list>
  </div>
</template>

<script lang="ts" setup>
  import { nextTick, reactive, ref } from 'vue';
  import { add, edit } from '@/views/app/customer/api/api-customer';
  import { list as getCategoryList } from '@/views/app/customer/api/api-category';
  import { list as getLevelList } from '@/views/app/customer/api/api-AppCustomerLevel';
  import type { categoryType } from '@/views/app/customer/types/category';
  import type { AppCustomerLevel } from '@/views/app/customer/types/AppCustomerLevel';
  import { Message } from '@arco-design/web-vue';
  import type { Customer } from '../types/customer';
  import LevelList from './level-list.vue';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const levelListRef = ref<InstanceType<typeof LevelList> | null>(null);

  const defaultForm: Customer = {
    id: undefined,
    name: '',
    contactName: '',
    mobile: '',
    phone: '',
    mail: '',
    postal: '',
    birthday: '',
    address: '',
    qq: '',
    note: '',
    status: 1,
    categoryId: undefined,
    categoryId_dictText: undefined,
    levelId: 0,
    levelId_dictText: undefined,
    discount: 100,
  };
  const form = reactive<Customer>({ ...defaultForm });
  const categoryTreeData = ref<categoryType[]>([]);
  const levelList = ref<AppCustomerLevel[]>([]);
  const loading = ref(false);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const fetchCategoryData = async () => {
    const res = await getCategoryList();
    const dataList = res.data;
    dataList.forEach((d) => {
      if (!d.children) d.children = [];
    });
    categoryTreeData.value = dataList;
  };

  const fetchLevelList = async () => {
    const res = await getLevelList();
    levelList.value = res.data;
  };

  const initData = async (item: Customer) => {
    await fetchCategoryData();
    await fetchLevelList();
    await nextTick();
    if (Object.keys(item).length !== 0) Object.assign(form, item);
  };

  const showModal = (item: Customer) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑';
    } else {
      title.value = '新增';
    }
    initData(item);
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
    levelList.value = [];
  };

  const handleOk = async () => {
    const s = await formRef.value.validate();
    console.log(form);

    if (!s) {
      // 验证通过后可继续操作
      loading.value = true;
      console.log('验证通过，提交表单数据:', form);
      try {
        if (!form.id) {
          await add(form);
        } else {
          await edit(form);
        }
      } finally {
        loading.value = false;
      }

      Message.success('操作成功');
      handleCancel();
      emit('ok', 1);
    }
  };

  defineExpose({ showModal });

  const openPriceModal = () => {
    levelListRef.value?.showModal();
  };

  const categoryFallback = (key: any) => {
    return {
      key: key || '0',
      title: form.categoryId_dictText || '',
    };
  };

  const levelFallback = (value: any) => {
    return {
      id: value || 0,
      title: form.levelId_dictText || '',
    };
  };
</script>

<style lang="less" scoped>
  .drawer {
  }
</style>
