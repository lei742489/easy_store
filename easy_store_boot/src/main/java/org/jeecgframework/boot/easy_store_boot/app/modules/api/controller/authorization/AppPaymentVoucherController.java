package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.*;
import org.jeecgframework.boot.easy_store_boot.app.common.excel.ExcelExportStylerBorder;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.*;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.*;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 付款单API接口
 */
@RestController
@RequestMapping("api/user/appPaymentVoucher")
public class AppPaymentVoucherController extends ApiBaseController<AppPaymentVoucher, IAppPaymentVoucherService> {

    @Autowired
    private IAppPaymentSettleItemService settleItemService;
    @Autowired
    private IAppPaymentAmountItemService amountItemService;
    @Autowired
    private IAppPurchaseOrderService purchaseOrderService;
    @Autowired
    private TaskSchedulerService taskSchedulerService;
    @Autowired
    private IAppSupplierService supplierService;
    @Autowired
    private IAppAccountSettleService accountSettleService;
    @Autowired
    private IAppUserService appUserService;

    @Value("${es-app.path.upload}")
    private String uploadPath;

    private final String  orderTag = "FKD";

    @Override
    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppPaymentVoucher entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        String supplierId = param.getString("supplierId");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;
        if (StringUtils.isNotEmpty(supplierId)) {
            entity.setSupplierId(null);
        }

        QueryWrapper<AppPaymentVoucher> queryWrapper =
                QueryGenerator.initQueryWrapper(entity, getDefaultAuditSortQueryParam(param));
        applyOwnerFilter(queryWrapper, param);
        applySupplierKeywordFilter(queryWrapper, supplierId);
        applyDefaultAuditSort(queryWrapper, param);
        IPage<AppPaymentVoucher> pageList =
                service.page(new Page<>(current, pageSize), queryWrapper);
        for (AppPaymentVoucher voucher : pageList.getRecords()) {
            maskPaymentVoucher(voucher, param);
        }
        return Result.ok(pageList);
    }

    @PostMapping("createOrderNo")
    public Result<?> createOrderNo(@RequestBody JSONObject param) {
        Result<String> result = new Result<>();
        result.setData(orderTag+ DateUtils.formatOrder(null));
        return result;
    }

    @PostMapping("approve")
    public Result<?> approve(@RequestBody JSONObject param) {
        assertRootForApprove(param);
        List<Integer> ids = getApproveIds(param);
        for (Integer id : ids) {
            AppPaymentVoucher voucher = service.getById(id);
            if (voucher == null || voucher.getStatus() == null || voucher.getStatus() != 0) {
                continue;
            }
            voucher.setStatus(1);
            voucher.setSettleItems(settleItemService.listByPaymentId(voucher.getId().toString()));
            voucher.setAmountItems(amountItemService.listByOrderId(voucher.getId().toString()));
            setDocumentUpdateBy(voucher, param);
            service.updateById(voucher);
        }
        return Result.ok();
    }

    /**
     * 导出excel
     *
     * @param request
     */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, AppPaymentVoucher object, String title) {
        // 过滤选中数据
        String selections = request.getParameter("selections");
        List<String> ids = Arrays.asList(selections.split(","));
        JSONObject permissionParam = getUserParam(request);
        List<AppPaymentVoucher> list = service.list(new LambdaQueryWrapper<AppPaymentVoucher>().in(AppPaymentVoucher::getId, ids).orderByDesc(AppPaymentVoucher::getCreateTime));

        for(AppPaymentVoucher item : list){
            translateDictValue(item) ;
            item.setSettleItems(settleItemService.listByPaymentId(item.getId().toString()));

            List<AppPaymentAmountItem> amountItems = amountItemService.listByOrderId(item.getId().toString());
            for(AppPaymentAmountItem amountItem : amountItems){
                AppPurchaseOrder purchaseOrder = purchaseOrderService.getByOrderNo(amountItem.getOrderNo());
                if(purchaseOrder != null){
                    amountItem.setPaidAmount(purchaseOrder.getPaidAmount());
                    amountItem.setUnpaidAmount(purchaseOrder.getUnpaidAmount());
                    amountItem.setPayableAmount(purchaseOrder.getPayableAmount());
                }
            }
            item.setAmountItems(amountItems);
            maskPaymentVoucher(item, permissionParam);
        }



        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        try {
            // Step 4：AutoPoi 导出
            if(StringUtils.isNotEmpty(title))
                title+="_明细";
            ExportParams exportParams = new ExportParams(title, "", title);
            exportParams.setStyle(ExcelExportStylerBorder.class);

            exportParams.setType(ExcelType.XSSF); // .xlsx 类型
            ApiModel apiModel = getEntityClass().getAnnotation(ApiModel.class);
            mv.addObject(NormalExcelConstants.FILE_NAME, apiModel.description() + "_" + System.currentTimeMillis()); // 文件名
            mv.addObject(NormalExcelConstants.CLASS, getEntityClass());     // 数据类型
            mv.addObject(NormalExcelConstants.PARAMS, exportParams); // 导出参数
            mv.addObject(NormalExcelConstants.DATA_LIST, list); // 数据
            return mv;
        }catch (Exception e){
            e.printStackTrace();
            return mv;
        }
    }

    @PostMapping(value = "/exportPdf")
    public Result<?>  exportPdf(@RequestBody JSONObject param, HttpServletResponse response) {
        AppPaymentVoucher entity = JSONObject.parseObject(param.toJSONString(), AppPaymentVoucher.class);

        AtomicReference<Double> totalAmount = new AtomicReference<>(0.0);
        entity.getSettleItems().forEach(item->{
            item.setSettleId(accountSettleService.getNameById(item.getSettleId()));
            totalAmount.set(DoubleUtil.add(totalAmount.get(), item.getAmount()));
        });
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(totalAmount.get())));

        Map<String,Object> map = new HashMap<>();

        map.put("obj",entity);
        map.put("total",totalAmount.get());

        if(entity.getSupplierId() == null)
            throw new AppRunTimeException("请选择供应商");

        map.put("supplier",supplierService.getById(entity.getSupplierId()));
        map.put("userInfo",appUserService.getById(param.getInteger("userId")));

        Result<Object> result = new Result<>();
        try {
            String subPath = "/report/付款单_" + System.currentTimeMillis() + ".pdf";
            String html = PdfReportUtils.generateHtml("PaymentVoucher.ftl",map );
            PdfReportUtils.generatePdf(html,uploadPath + subPath);
            JSONObject obj = new JSONObject();
            obj.put("subPath", subPath);
            obj.put("filePath",uploadPath + subPath);
            result.setData(obj);
            File pdfFile = new File(uploadPath + subPath);
            if(pdfFile.exists()){
                taskSchedulerService.schedule(pdfFile.getName(),20, TimeUnit.SECONDS, pdfFile::delete);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new AppRunTimeException("pdf文件导出异常：" + e.getMessage());
        }

        return result;

    }

    @PostMapping(value = "/exportHtml")
    public Result<?> exportHtml(@RequestBody JSONObject param) {
        AppPaymentVoucher entity = JSONObject.parseObject(param.toJSONString(), AppPaymentVoucher.class);

        if (entity == null || entity.getSettleItems() == null || entity.getSettleItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }

        AtomicReference<Double> totalAmount = new AtomicReference<>(0.0);
        entity.getSettleItems().forEach(item -> {
            item.setSettleId(accountSettleService.getNameById(item.getSettleId()));
            totalAmount.set(DoubleUtil.add(totalAmount.get(), item.getAmount()));
        });
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(totalAmount.get())));

        Map<String,Object> map = new HashMap<>();
        map.put("obj", entity);
        map.put("total", totalAmount.get());

        if(entity.getSupplierId() == null) {
            throw new AppRunTimeException("请选择供应商");
        }
        map.put("supplier", supplierService.getById(entity.getSupplierId()));
        map.put("userInfo", appUserService.getById(param.getInteger("userId")));

        Result<Object> result = new Result<>();
        try {
            String html = PdfReportUtils.generateHtml("PaymentVoucher.ftl", map);
            JSONObject obj = new JSONObject();
            obj.put("html", html);
            obj.put("jobName", "付款单_" + entity.getOrderNo());
            result.setData(obj);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AppRunTimeException("html文件导出异常：" + e.getMessage());
        }

        return result;
    }

    @PostMapping(value = "/exportEscp")
    public Result<?> exportEscp(@RequestBody JSONObject param) {
        AppPaymentVoucher entity = JSONObject.parseObject(param.toJSONString(), AppPaymentVoucher.class);
        if (entity == null || entity.getSettleItems() == null || entity.getSettleItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }
        AtomicReference<Double> totalAmount = new AtomicReference<>(0.0);
        entity.getSettleItems().forEach(item -> {
            item.setSettleId(accountSettleService.getNameById(item.getSettleId()));
            totalAmount.set(DoubleUtil.add(totalAmount.get(), item.getAmount()));
        });
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(totalAmount.get())));
        if (entity.getSupplierId() == null) {
            throw new AppRunTimeException("请选择供应商");
        }
        AppSupplier supplier = supplierService.getById(entity.getSupplierId());
        byte[] bytes = EscpReportUtils.paymentVoucher(
                entity,
                supplier,
                appUserService.getById(param.getInteger("userId")),
                totalAmount.get()
        );
        JSONObject obj = new JSONObject();
        obj.put("data", java.util.Base64.getEncoder().encodeToString(bytes));
        obj.put("jobName", "付款单_" + entity.getOrderNo());
        return Result.ok(obj);
    }

    private void assertRootForApprove(JSONObject param) {
        if (!isRootUser(param)) {
            throw new AppRunTimeException("只有老板账号可以审核单据");
        }
    }

    private List<Integer> getApproveIds(JSONObject param) {
        List<Integer> ids = new ArrayList<>();
        if (param.getJSONArray("ids") != null) {
            for (Object id : param.getJSONArray("ids")) {
                if (id != null) ids.add(Integer.valueOf(id.toString()));
            }
        }
        Integer id = param.getInteger("id");
        if (id != null) {
            ids.add(id);
        }
        return ids;
    }

    private void maskPaymentVoucher(AppPaymentVoucher voucher, JSONObject param) {
        if (voucher == null) return;
        if (!hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_PURCHASE_PRICE)) {
            voucher.setAmount(0D);
            if (voucher.getSettleItems() != null) {
                for (AppPaymentSettleItem item : voucher.getSettleItems()) {
                    if (item != null) item.setAmount(0D);
                }
            }
            if (voucher.getAmountItems() != null) {
                for (AppPaymentAmountItem item : voucher.getAmountItems()) {
                    if (item != null) {
                        item.setAmount(0D);
                        item.setPayableAmount(0D);
                        item.setPaidAmount(0D);
                        item.setUnpaidAmount(0D);
                    }
                }
            }
        }
    }
}
