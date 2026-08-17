<template>
  <div class="drawer">
    <a-modal
      :width="700"
      :visible="visible"
      unmount-on-close
      :mask-closable="true"
      :ok-loading="loading"
      @ok="handleOk"
      @cancel="handleCancel"
    >
      <template #title> {{ title }} </template>
      <div>
        <a-form ref="formRef" :model="form" auto-label-width>
          <a-form-item
            field="title"
            label="货品名称"
            :rules="[{ required: true, message: '请输入货品名称' }]"
          >
            <a-input
              v-model="form.title"
              placeholder="请输入货品名称"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="supplierTitle" label="供应商货品名">
            <a-input
              v-model="form.supplierTitle"
              placeholder="请输入供应商货品名"
              :max-length="100"
            />
          </a-form-item>

          <a-row :gutter="24">
            <a-col :span="16">
              <a-form-item field="goodsCode" label="货品代码">
                <a-input
                  v-model="form.goodsCode"
                  placeholder="请输入货品代码"
                  :max-length="100"
                />
              </a-form-item>
              <a-form-item
                field="categoryId"
                label="货品分类"
                :rules="[
                  {
                    required: true,
                    message: '\u8bf7\u9009\u62e9\u8d27\u54c1\u5206\u7c7b',
                  },
                ]"
              >
                <category-select-tree
                  ref="categoryTreeRef"
                  v-model:category-id="form.categoryId"
                  v-model:category-text="form.categoryId_dictText"
                ></category-select-tree>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item field="imgUrl" label="缩略图">
                <image-upload ref="uploadRef" v-model="form.imgUrl" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item field="unit" label="单位" class="def-select">
                <a-select
                  v-model="form.unit"
                  :options="unitList"
                  :field-names="{ value: 'name', label: 'name' }"
                  :allow-search="true"
                  :allow-create="true"
                  :allow-clear="true"
                  :loading="unitLoading"
                  placeholder="请选择"
                >
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="16">
              <a-form-item field="supplierId" label="供应商">
                <a-tree-select
                  v-model="form.supplierId"
                  :loading="supplierLoading"
                  :data="supplierList"
                  placeholder="请选择..."
                  :field-names="{ key: 'id', title: 'name' }"
                  :filter-tree-node="filterSupplierTreeNode"
                  :allow-search="true"
                  :allow-clear="true"
                  :fallback-option="supplierFallback"
                  @popup-visible-change="fetchSupplierData"
                ></a-tree-select>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item field="initCost" label="初始成本">
                <a-input-number
                  v-model="form.initCost"
                  :disabled="form.id != null"
                  placeholder="0.00"
                  :min="0"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item field="initStock" label="初始库存">
                <a-input-number
                  v-model="form.initStock"
                  placeholder="0"
                  :disabled="true"
                  :min="0"
                />
              </a-form-item>
            </a-col>

            <a-col :span="8">
              <a-form-item field="stock" label="当前库存">
                <a-input-number
                  v-model="form.stock"
                  :disabled="form.id !== undefined"
                  placeholder="0"
                  :min="0"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item field="stockCost" label="库存总成本">
                <a-input-number
                  v-model="form.stockCost"
                  placeholder="0.00"
                  :precision="2"
                  :disabled="true"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item field="costPrice" label="成本价">
                <a-input-number
                  v-model="form.costPrice"
                  placeholder="0.00"
                  :precision="2"
                  :disabled="true"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item field="salePrc" label="零售价">
                <a-input-number
                  v-model="form.salePrc"
                  placeholder="0.00"
                  :min="0"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item field="tradePrc" label="批发价">
                <a-input-number
                  v-model="form.tradePrc"
                  placeholder="0.00"
                  :min="0"
                />
              </a-form-item>
            </a-col>

            <a-col :span="8">
              <a-form-item field="purPrc" label="进货价">
                <a-input-number
                  v-model="form.purPrc"
                  placeholder="0.00"
                  :min="0"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item field="maxStock" label="最大库存">
                <a-input-number
                  v-model="form.maxStock"
                  placeholder="0"
                  :min="0"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item field="minStock" label="最小库存">
                <a-input-number
                  v-model="form.minStock"
                  placeholder="0"
                  :min="0"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item field="note" label="备注">
            <a-textarea
              v-model="form.note"
              placeholder="请输入备注"
              :max-length="100"
            />
          </a-form-item>
          <a-form-item field="status" label="状态">
            <a-select v-model="form.status" placeholder="请选择 ...">
              <a-option :value="1">启用</a-option>
              <a-option :value="0">禁用</a-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import imageUpload from '@/components/upload/image-upload.vue';
  import { list as getSupplierList } from '@/views/app/AppSupplier/api/api-AppSupplier';
  import { AppSupplier } from '@/views/app/AppSupplier/types/AppSupplier';
  import type { AppGoodsCategory } from '@/views/app/goods/types/AppGoodsCategory';
  import CategorySelectTree from '@/views/app/goods/components/category-select-tree.vue';
  import { list as getCategoryList } from '../api/api-AppGoodsCategory';
  import type { AppGoods, AppUnit } from '../types/AppGoods';
  import { add, edit, getUnitList } from '../api/api-AppGoods';

  const visible = ref(false);
  const formRef = ref();
  const title = ref('');
  const supplierLoading = ref(false);
  const unitLoading = ref(false);
  const uploadRef = ref(null);

  const defaultForm: AppGoods = {
    id: undefined,
    title: undefined,
    supplierTitle: undefined,
    categoryId: undefined,
    categoryId_dictText: undefined,
    goodsCode: undefined,
    imgUrl: '',
    initCost: undefined,
    initStock: undefined,
    stock: undefined,
    stockCost: undefined,
    costPrice: undefined,
    unit: undefined,
    salePrc: undefined,
    tradePrc: undefined,
    purPrc: undefined,
    maxStock: undefined,
    minStock: undefined,
    supplierId: undefined,
    supplierId_dictText: undefined,
    note: undefined,
    status: 1,
  };
  const form = reactive<AppGoods>({ ...defaultForm });
  const loading = ref(false);
  const supplierList = ref<AppSupplier[]>([]);
  const unitList = ref<AppUnit[]>([]);

  const emit = defineEmits<{
    (e: 'ok', data: 1): void;
  }>();

  const fetchSupplierData = async (e: any) => {
    if (supplierList.value.length !== 0) return;

    supplierLoading.value = true;
    supplierList.value = (await getSupplierList()).data;
    supplierLoading.value = false;
  };

  const fetchUnitData = async () => {
    if (unitList.value.length !== 0) return;
    unitLoading.value = true;
    unitList.value = (await getUnitList()).data;
    unitLoading.value = false;
  };

  const showModal = (item: AppGoods) => {
    visible.value = true;
    if (item.id) {
      title.value = '编辑-货品管理';
    } else {
      title.value = '新增-货品管理';
    }
    fetchUnitData();
    if (Object.keys(item).length !== 0) Object.assign(form, item);
    if (item.imgUrl) {
      setTimeout(() => {
        form.imgUrl = `${item.imgUrl} `;
      }, 250);
    }
  };

  const handleCancel = () => {
    visible.value = false;
    Object.assign(form, defaultForm);
    supplierList.value = [];
    unitList.value = [];
  };

  const handleOk = async () => {
    const s = await formRef.value.validate();
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

  const filterSupplierTreeNode = (
    searchValue: string,
    nodeData: AppSupplier
  ) => {
    const t = nodeData.name || '';
    const pyCode = nodeData.pyCode || '';
    const key = searchValue.toLowerCase();
    return t.toLowerCase().indexOf(key) > -1 || pyCode.indexOf(key) > -1;
  };

  const unitRemove = (name: string) => {
    const removeList: string[] = [];
    unitList.value.forEach((item) => {
      if (item.name === name) {
        removeList.push(item.name);
      }
    });

    unitList.value = unitList.value.filter((item) => item.name !== name);
  };

  const supplierFallback = (key: any) => {
    return {
      key: key || 0,
      name: form.supplierId_dictText || '',
    };
  };

  defineExpose({ showModal });
</script>

<style lang="less" scoped>
  .drawer {
  }

  .del-icon:hover {
    transform: scale(1.5);
  }
</style>
