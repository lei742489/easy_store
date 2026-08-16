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
                    <#list fields as field>
                    <#if field.name != "id" && field.name != "createTime"   && field.name != "status">
                        <a-form-item
                                field="${field.name}"
                                label="${field.label}"
                                <#if field.required>
                                :rules="[{ required: true, message: '请输入${field.label}' }]"
                                </#if>
                        >
                            <a-input
                                    v-model="form.${field.name}"
                                    placeholder="请输入${field.label}"
                                    :max-length="100"
                            />
                        </a-form-item>
                    </#if>
                    <#if field.name == "status">
                        <a-form-item field="status" label="状态">
                            <a-select v-model="form.status" placeholder="请选择 ...">
                                <a-option :value="1">启用</a-option>
                                <a-option :value="0">禁用</a-option>
                            </a-select>
                        </a-form-item>
                    </#if>
                    </#list>
                </a-form>
            </div>
        </a-drawer>
    </div>
</template>

<script lang="ts" setup>
    import { reactive, ref } from 'vue';
    import type { ${className} } from '../types/${className}';
    import { add, edit } from '../api/api-${className}';
    import { Message } from '@arco-design/web-vue';

    const visible = ref(false);
    const formRef = ref();
    const title = ref('');

    const defaultForm: ${className} = {
        <#list fields as field>
        <#if field.name != "status">
        ${field.name}: undefined,
        </#if>
        <#if field.name == "status">
        ${field.name}: 1,
        </#if>
        </#list>
    };
    const form = reactive<${className}>({ ...defaultForm });
    const loading = ref(false);

    const emit = defineEmits<{
    (e: 'ok', data: 1): void;
    }>();

    const showModal = (item: ${className}) => {
        visible.value = true;
        if (item.id) {
            title.value = '编辑-${title}';
        } else {
            title.value = '新增-${title}';
        }
        if (Object.keys(item).length !== 0) Object.assign(form, item);
    };

    const handleCancel = () => {
        visible.value = false;
        Object.assign(form, defaultForm);
    };

    const handleOk = async () => {
        const s = await formRef.value.validate();
        console.log(s);
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
</script>

<style lang="less" scoped>
    .drawer {
    }
</style>
