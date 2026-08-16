<template>
    <div class="container">
        <a-card
                class="general-card"
                title="${title}"
                style="margin-top: 12px"
        >
            <a-row>
                <a-col :flex="1">
                    <a-form
                            :model="formModel"
                            :label-col-props="{ span: 4 }"
                            :wrapper-col-props="{ span: 18 }"
                            :label-width="10"
                            label-align="center"
                    >
                        <a-row :gutter="24">
                            <a-col :span="10">
                                <a-form-item field="status" label="状态">
                                    <a-select v-model="formModel.status" placeholder="请选择 ...">
                                        <a-option :value="1">启用</a-option>
                                        <a-option :value="0">禁用</a-option>
                                    </a-select>
                                </a-form-item>
                            </a-col>

                        </a-row>
                    </a-form>
                </a-col>
                <a-divider style="height: 84px" direction="vertical" />
                <a-col :flex="'86px'" style="text-align: right">
                    <a-space direction="vertical" :size="18">
                        <a-button type="primary" @click="search()">
                            <template #icon>
                                <icon-search />
                            </template>
                            查询
                        </a-button>
                        <a-button @click="reset">
                            <template #icon>
                                <icon-refresh />
                            </template>
                            重置
                        </a-button>
                    </a-space>
                </a-col>
            </a-row>
            <a-divider style="margin-top: 4px" />
            <a-row style="margin-bottom: 16px">
                <a-col :span="12">
                    <a-space>
                        <a-button type="primary" @click="handelEdit({})">
                            <template #icon>
                                <icon-plus />
                            </template>
                            新增
                        </a-button>
                        <a-upload :custom-request="uploadExcel" :show-file-list="false">
                            <template #upload-button>
                                <a-button :loading="uploadLoading">
                                    <template #icon> <icon-upload /> </template>导入
                                </a-button>
                            </template>
                        </a-upload>

                        <a-button @click="exportXlsFile(formModel)">
                            <template #icon>
                                <icon-download />
                            </template>
                            导出
                        </a-button>
                    </a-space>
                </a-col>

                <a-col
                        :span="12"
                        style="
            display: flex;
            align-items: center;
            justify-content: end;
            gap: 10px;
          "
                >
                    <a-tooltip content="打印表格">
                        <a-button v-print="'#a-table'" type="text"
                        ><icon-printer size="18"
                            /></a-button>
                    </a-tooltip>
                    <a-tooltip content="刷新">
                        <div class="action-icon" @click="search()"
                        ><icon-refresh size="18"
                            /></div>
                    </a-tooltip>
                    <a-dropdown @select="handleSelectDensity">
                        <a-tooltip content="密度">
                            <div class="action-icon"><icon-line-height size="18" /></div>
                        </a-tooltip>
                        <template #content>
                            <a-doption
                                    v-for="item in densityList"
                                    :key="item.value"
                                    :value="item.value"
                                    :class="{ active: item.value === size }"
                            >
                                <span>{{ item.name }}</span>
                            </a-doption>
                        </template>
                    </a-dropdown>
                </a-col>
            </a-row>

            <a-table
                    id="a-table"
                    row-key="id"
                    :loading="loading"
                    :pagination="pagination"
                    :columns="(columns as TableColumnData[])"
                    :data="renderData"
                    :bordered="{ cell: true }"
                    :size="size"
                    :scrollbar="true"
                    :scroll="{ x: '100%', y: 540 }"
                    @page-change="onPageChange"
                    @row-dblclick="dbRowClick"
                    @sorter-change="onSorterChange"
            >
                <template #index="{ rowIndex }">
                    {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
                </template>
                <template #status="{ record }">
          <span
                  :style="record.status != 1 ? 'color:#eb4d4b;' : 'color:#0984e3;'"
          >{{ record.status == 1 ? '启用' : '禁用' }}</span
          >
                </template>

                <template #operations="{ record }">
                    <a-button type="text" size="small" @click="handelEdit(record)">
                        编辑</a-button
                    >
                    <a-divider style="margin: 0" direction="vertical" />
                    <a-popconfirm
                            :content="`确认删除该条数据?`"
                            @ok="handelRemove(record)"
                    >
                        <a-button type="text" size="small">删除</a-button>
                    </a-popconfirm>
                </template>
            </a-table>
        </a-card>
        <form-modal ref="modalRef" @ok="search(pagination)"></form-modal>
    </div>
</template>

<script lang="ts" setup>
    import { computed,  reactive, ref } from 'vue';
    import { Pagination } from '@/types/global';
    import useLoading from '@/hooks/loading';
    import { PolicyParams } from '@/api/list';
    import type { TableColumnData } from '@arco-design/web-vue/es/table/interface';
    import { Message } from '@arco-design/web-vue';
    import FormModal from './components/modal.vue';

    import { listPage, remove,exportXlsFile,importExcel } from './api/api-${className}';
    import type { ${className} } from './types/${className}';

    const { loading, setLoading } = useLoading(false);
    type SizeProps = 'mini' | 'small' | 'medium' | 'large';
    const modalRef = ref<InstanceType<typeof FormModal> | null>(null);
    const uploadLoading = ref<boolean>(false);

    const densityList = computed(() => [
        {
            name: '迷你',
            value: 'mini',
        },
        {
            name: '偏小',
            value: 'small',
        },
        {
            name: '中等',
            value: 'medium',
        },
        {
            name: '偏大',
            value: 'large',
        },
    ]);

    const generateFormModel = (): ${className} => {
        return {
            <#list fields as field>
            ${field.name}: undefined,
            </#list>

        };
    };

    const formModel = ref(generateFormModel());
    const size = ref<SizeProps>('medium');
    const renderData = ref<${className}[]>([]);

    const basePagination: Pagination = {
        current: 1,
        pageSize: 50,
        order: 'desc',
        column: 'createTime',
        showTotal: true,
    };

    const pagination = reactive({
        ...basePagination,
    });

    const columns = computed<TableColumnData[]>(() => [
        {
            title: '序号',
            dataIndex: 'index',
            slotName: 'index',
            align: 'center',
        },
        <#list fields as field>
        <#if field.name != "id">
        {
            title: '${field.label}',
            dataIndex: '${field.name}',
            align: 'center',
        },
        </#if>
        </#list>

        {
            title: '操作',
            dataIndex: 'operations',
            slotName: 'operations',
            align: 'center',
            width: 180,
        },
    ]);

    const fetchData = async (
        params: PolicyParams = {
            current: 1,
            pageSize: 50,
            order: 'desc',
            column: 'createTime',
        }
    ) => {
        setLoading(true);
        try {
            const { data } = await listPage(params);
            renderData.value = data.records!;
            pagination.total = data.total;
            pagination.current = data.current || 1;
        } finally {
            setLoading(false);
        }
    };

    const search = (p?: Pagination) => {
        const pg: Pagination = p || basePagination;
        fetchData({
            ...pg,
            ...formModel.value,
        } as unknown as PolicyParams);
    };

    const onPageChange = (current: number) => {
        fetchData({ ...basePagination, current });
    };

    fetchData();
    const reset = () => {
        formModel.value = generateFormModel();
        search();
    };

    const handleSelectDensity = (
        val: string | number | Record<string, any> | undefined,
        e: Event
    ) => {
        size.value = val as SizeProps;
    };

    const handelRemove = async (data: ${className}) => {
        await remove(data);
        Message.success('操作成功');
        search();
    };

    const handelEdit = (item: ${className}) => {
        modalRef.value?.showModal(item);
    };

    const dbRowClick = (record: ${className}, rowIndex: number) => {
        handelEdit(record);
    };

    const uploadExcel = async (e: any) => {
        if (uploadLoading.value) return;
        uploadLoading.value = true;

        try {
            const res: any = await importExcel(e.fileItem.file);
            Message.success(res.message!);
            search();
        } catch (error: any) {
            Message.error('上传错误：' + error);
        } finally {
            uploadLoading.value = false;
        }
    };

    const onSorterChange = (column: string, order: string) => {
        basePagination.column = column;
        basePagination.order = order.replace('end', '');
        search();
    };
</script>

<style lang="less" scoped>
    .container {
    }
</style>
