<template>
  <a-modal
    v-model:visible="visible"
    title="C-Lodop 打印"
    width="460px"
    :footer="false"
    :mask-closable="true"
    unmount-on-close
  >
    <div class="clodop-print-modal">
      <a-form :model="form" auto-label-width>
        <a-alert v-if="!clodopReady" type="warning" style="margin-bottom: 12px">
          未检测到 C-Lodop 打印服务，将使用浏览器打印。
        </a-alert>

        <a-form-item v-if="clodopReady" field="printerName" label="选择打印机">
          <a-select
            v-model="form.printerName"
            :loading="loadingPrinters"
            placeholder="请选择打印机"
            allow-search
          >
            <a-option
              v-for="printer in printers"
              :key="printer.name"
              :value="printer.name"
            >
              {{ printer.name }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item v-if="clodopReady" field="paperKey" label="纸张">
          <a-select v-model="form.paperKey" placeholder="请选择纸张">
            <a-option
              v-for="paper in paperOptions"
              :key="paper.value"
              :value="paper.value"
            >
              {{ paper.label }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>

      <div class="print-actions">
        <a-button :loading="previewLoading" @click="handlePreview">
          <template #icon>
            <icon-file-pdf />
          </template>
          打印预览
        </a-button>
        <a-button type="primary" :loading="printLoading" @click="handlePrint">
          <template #icon>
            <icon-printer />
          </template>
          打印
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
  import { computed, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import {
    getCLodopPrinters,
    previewHtmlWithCLodop,
    previewPdfWithCLodop,
    printHtmlWithCLodop,
    printPdfWithCLodop,
    type CLodopPaperSize,
    type CLodopPrinter,
  } from '@/utils/clodop';

  interface PrintOptions {
    jobName: string;
    pdfUrl?: string;
    htmlContent?: string;
  }

  const paperOptions = [
    { label: '一等分（241 × 280 mm）', value: 'one', width: 241, height: 280 },
    { label: '二等分（241 × 140 mm）', value: 'two', width: 241, height: 140 },
    { label: '三等分（241 × 93 mm）', value: 'three', width: 241, height: 93 },
    { label: '小票（58mm）', value: 'ticket58', width: 58, height: 0 },
    { label: '小票（80mm）', value: 'ticket80', width: 80, height: 0 },
    { label: '小票（110mm）', value: 'ticket110', width: 110, height: 0 },
    { label: 'A4（210 × 297 mm）', value: 'a4', width: 210, height: 297 },
  ] as const;

  type PaperKey = (typeof paperOptions)[number]['value'];

  const CACHE_PRINTER_KEY = 'easy-store-clodop-printer';
  const CACHE_PAPER_KEY = 'easy-store-clodop-paper';

  const visible = ref(false);
  const loadingPrinters = ref(false);
  const previewLoading = ref(false);
  const printLoading = ref(false);
  const clodopReady = ref(true);
  const printers = ref<CLodopPrinter[]>([]);
  const printOptions = ref<PrintOptions | null>(null);
  const form = reactive({
    printerName: '',
    paperKey: 'one' as PaperKey,
  });

  const currentPaper = computed<CLodopPaperSize>(() => {
    const paper = paperOptions.find((item) => item.value === form.paperKey);
    return {
      width: paper?.width ?? 241,
      height: paper?.height ?? 280,
      name: paper?.label ?? '',
    };
  });

  const selectDefaultPrinter = () => {
    const cachedName = localStorage.getItem(CACHE_PRINTER_KEY) || '';
    const cachedPrinter = printers.value.find(
      (printer) => printer.name === cachedName
    );
    form.printerName = cachedPrinter?.name || printers.value[0]?.name || '';
  };

  const selectDefaultPaper = () => {
    const cachedKey = localStorage.getItem(CACHE_PAPER_KEY) || '';
    const cachedPaper = paperOptions.find((item) => item.value === cachedKey);
    form.paperKey = (cachedPaper?.value || paperOptions[0].value) as PaperKey;
  };

  const loadPrinters = async () => {
    loadingPrinters.value = true;
    try {
      printers.value = await getCLodopPrinters();
      clodopReady.value = true;
      selectDefaultPrinter();
      selectDefaultPaper();
    } catch (error) {
      clodopReady.value = false;
      printers.value = [];
      form.printerName = '';
      Message.warning(
        error instanceof Error
          ? `${error.message}，已切换为浏览器打印`
          : '读取打印机失败，已切换为浏览器打印'
      );
    } finally {
      loadingPrinters.value = false;
    }
  };

  const getOptions = () => {
    if (!printOptions.value?.pdfUrl && !printOptions.value?.htmlContent) {
      throw new Error('没有可打印内容');
    }
    if (clodopReady.value && !form.printerName) {
      throw new Error('请选择打印机');
    }
    return printOptions.value;
  };

  const openBrowserPrintWindow = (options: PrintOptions, autoPrint: boolean) => {
    if (options.pdfUrl) {
      window.open(options.pdfUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    if (!options.htmlContent) {
      throw new Error('没有可打印内容');
    }

    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      throw new Error('浏览器阻止了打印窗口，请允许弹窗后重试');
    }
    const html = /<html[\s>]/i.test(options.htmlContent)
      ? options.htmlContent
      : `<!doctype html><html><head><title>${options.jobName}</title></head><body>${options.htmlContent}</body></html>`;
    printWindow.document.open();
    printWindow.document.write(html);
    printWindow.document.close();
    if (autoPrint) {
      window.setTimeout(() => {
        printWindow.focus();
        printWindow.print();
      }, 300);
    }
  };

  const handlePreview = async () => {
    previewLoading.value = true;
    try {
      const options = getOptions();
      if (!clodopReady.value) {
        openBrowserPrintWindow(options, false);
      } else if (options.htmlContent) {
        await previewHtmlWithCLodop(
          options.htmlContent,
          options.jobName,
          form.printerName,
          currentPaper.value
        );
      } else if (options.pdfUrl) {
        await previewPdfWithCLodop(
          options.pdfUrl,
          options.jobName,
          form.printerName,
          currentPaper.value
        );
      }
    } catch (error) {
      Message.error(error instanceof Error ? error.message : '打印预览失败');
    } finally {
      previewLoading.value = false;
    }
  };

  const handlePrint = async () => {
    printLoading.value = true;
    try {
      const options = getOptions();
      if (!clodopReady.value) {
        openBrowserPrintWindow(options, true);
      } else if (options.htmlContent) {
        await printHtmlWithCLodop(
          options.htmlContent,
          options.jobName,
          form.printerName,
          currentPaper.value
        );
      } else if (options.pdfUrl) {
        await printPdfWithCLodop(
          options.pdfUrl,
          options.jobName,
          form.printerName,
          currentPaper.value
        );
      }
      if (clodopReady.value) {
        localStorage.setItem(CACHE_PRINTER_KEY, form.printerName);
        localStorage.setItem(CACHE_PAPER_KEY, form.paperKey);
      }
      Message.success('打印任务已发送');
      visible.value = false;
    } catch (error) {
      Message.error(
        error instanceof Error ? error.message : 'C-Lodop 打印失败'
      );
    } finally {
      printLoading.value = false;
    }
  };

  const show = async (options: PrintOptions) => {
    printOptions.value = options;
    visible.value = true;
    await loadPrinters();
  };

  defineExpose({ show });
</script>

<style lang="less" scoped>
  .clodop-print-modal {
    padding-top: 4px;
  }

  .print-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 18px;
  }
</style>
