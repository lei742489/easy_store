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
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentAmountItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrder;


import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrderItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.*;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * 进货单API接口
 */
@RestController
@RequestMapping("api/user/appPurchaseOrder")
public class AppPurchaseOrderController extends ApiBaseController<AppPurchaseOrder, IAppPurchaseOrderService> {
    @Autowired
    private IAppPurchaseOrderItemService appPurchaseOrderItemService;
    @Autowired
    private IAppGoodsService appGoodsService;
    @Autowired
    private TaskSchedulerService taskSchedulerService;
    @Autowired
    private IAppPaymentAmountItemService paymentAmountItemService;
    @Autowired
    private IAppSupplierService appSupplierService;
    @Autowired
    private IAppUserService appUserService;

    @Value("${es-app.path.upload}")
    private String uploadPath;

    private final String  orderTag = "EPO";

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppPurchaseOrder entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        Integer unpaidOnly = param.getInteger("unpaidOnly");
        String supplierId = param.getString("supplierId");
        Integer orderId = param.getInteger("orderId");
        if(orderId!=null){
            return queryByVoucherOrder(orderId, param);
        }

        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;

        String searchKey = param.getString("searchKey");
        if (StringUtils.isNotEmpty(supplierId)) {
            entity.setSupplierId(null);
        }
        QueryWrapper<AppPurchaseOrder> queryWrapper = QueryGenerator.initQueryWrapper(entity, getDefaultAuditSortQueryParam(param));
        applyOwnerFilter(queryWrapper, param);
        applySupplierKeywordFilter(queryWrapper, supplierId);

        if(unpaidOnly!=null){
            queryWrapper.ne("unpaid_amount",0).eq("status", 1);
        }


        if(StringUtils.isNotEmpty(searchKey)){
            queryWrapper.and(w -> w.like("order_no",searchKey)
                    .or().like("note",searchKey)
                    .or().apply("id IN (select poi.order_id from app_purchase_order_item poi " +
                            "left join app_goods g on g.id = poi.goods_id " +
                            "where g.title like {0} or CAST(poi.goods_id AS CHAR) like {1})",
                            "%" + searchKey + "%", "%" + searchKey + "%"));
        }
        applyDefaultAuditSort(queryWrapper, param);
        Page<AppPurchaseOrder> page = new Page<>(current, pageSize);
        IPage<AppPurchaseOrder> pageList = service.page(page, queryWrapper);
        for(AppPurchaseOrder appPurchaseOrder : pageList.getRecords()){
            appPurchaseOrder.setItems(appPurchaseOrderItemService.listByOrderId(appPurchaseOrder.getId()));
            fillPurchaseOrderItemGoodsCode(appPurchaseOrder.getItems());
            maskPurchaseOrder(appPurchaseOrder, param);
        }
        return Result.ok(pageList);
    }

    private void fillPurchaseOrderItemGoodsCode(List<AppPurchaseOrderItem> items) {
        if(items == null) return;
        for(AppPurchaseOrderItem item : items) {
            if(item == null || StringUtils.isBlank(item.getGoodsId())) continue;
            AppGoods goods = appGoodsService.getById(item.getGoodsId());
            if(goods != null) item.setGoodsCode(goods.getGoodsCode());
        }
    }

    @PostMapping("createOrderNo")
    public Result<?> createOrderNo(@RequestBody JSONObject param) {
        Result<String> result = new Result<>();
        result.setData(orderTag+DateUtils.formatOrder(null));
        return result;
    }

    @PostMapping("approve")
    public Result<?> approve(@RequestBody JSONObject param) {
        assertRootForApprove(param);
        List<Integer> ids = getApproveIds(param);
        for (Integer id : ids) {
            AppPurchaseOrder order = service.getById(id);
            if (order == null || order.getStatus() == null || order.getStatus() != 0) {
                continue;
            }
            order.setStatus(1);
            order.setItems(appPurchaseOrderItemService.listByOrderId(order.getId()));
            setDocumentUpdateBy(order, param);
            service.updateById(order);
        }
        return Result.ok();
    }

    /**
     * 导出excel
     *
     * @param request
     */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, AppPurchaseOrder object,  String title) {
        // 过滤选中数据
        String selections = request.getParameter("selections");
        List<String> ids = Arrays.asList(selections.split(","));
        JSONObject permissionParam = getUserParam(request);
        List<AppPurchaseOrder> list = service.list(new LambdaQueryWrapper<AppPurchaseOrder>().in(AppPurchaseOrder::getId, ids).orderByDesc(AppPurchaseOrder::getCreateTime));

        for(AppPurchaseOrder order : list){
            translatePurchaseOrderForExport(order);
            order.setItems(appPurchaseOrderItemService.listByOrderId(order.getId()));
            maskPurchaseOrder(order, permissionParam);
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

    /**
     * 通过excel导入数据
     *
     * @param response
     * @return
     */
    @RequestMapping(value = "/importExcel")
    public Result<?> importExcel(@RequestParam("file") MultipartFile file, HttpServletResponse response) {
        ImportParams params = new ImportParams();
        params.setHeadRows(1);
        params.setNeedSave(true);

        try (InputStream is = file.getInputStream()) {
            List<AppPurchaseOrder> list = ExcelImportUtil.importExcel(is, getEntityClass(), params);
            if(list.size()>1000)
                throw new AppRunTimeException("单次导入不能超过1000条数据");
            int idx = 0;
            for (AppPurchaseOrder appPurchaseOrder : list) {
                if(appPurchaseOrder.getItems()==null || appPurchaseOrder.getItems().isEmpty()) continue;
                appPurchaseOrder.setOrderNo(orderTag+DateUtils.formatOrder(null)+idx);
                service.save(appPurchaseOrder);
                appPurchaseOrder.getItems().forEach(item->{
                    item.setOrderId(appPurchaseOrder.getId());
                });
                idx++;
            }
            //  service.saveBatch(list);
            return Result.ok("文件导入成功！数据行数：" + list.size());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件导入失败:" + e.getMessage());
        }
    }


    @PostMapping(value = "/exportPdf")
    public Result<?>  exportPdf(@RequestBody JSONObject param, HttpServletResponse response) {
        AppPurchaseOrder entity = JSONObject.toJavaObject(param, AppPurchaseOrder.class);
        Result<Object> result = new Result<>();

        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }


        for (AppPurchaseOrderItem item : entity.getItems()) {
            item.setGoodsId(appGoodsService.getTitleById(item.getGoodsId()));
        }
        entity.setCreateTime(new Date());
        if (entity.getPaidAmount() == null) entity.setPaidAmount(0.00);
        if (entity.getFreightAmount() == null) entity.setFreightAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(), entity.getPaidAmount()));
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(entity.getTotalAmount())));

        JSONObject pdfObj = new JSONObject();
        pdfObj.putIfAbsent("obj",entity);
        pdfObj.put("supplier",appSupplierService.getById(entity.getSupplierId()));
        pdfObj.put("userInfo",appUserService.getById(param.getInteger("userId")));

        translateDictValue(entity);
        try {
            String subPath = "/report/进货单_" + System.currentTimeMillis() + ".pdf";
            String html = PdfReportUtils.generateHtml("PurchaseOrder.ftl",pdfObj );
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
        AppPurchaseOrder entity = JSONObject.toJavaObject(param, AppPurchaseOrder.class);
        Result<Object> result = new Result<>();

        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }

        for (AppPurchaseOrderItem item : entity.getItems()) {
            item.setGoodsId(appGoodsService.getTitleById(item.getGoodsId()));
        }
        entity.setCreateTime(new Date());
        if (entity.getPaidAmount() == null) entity.setPaidAmount(0.00);
        if (entity.getFreightAmount() == null) entity.setFreightAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(), entity.getPaidAmount()));
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(entity.getTotalAmount())));

        JSONObject htmlObj = new JSONObject();
        htmlObj.putIfAbsent("obj", entity);
        htmlObj.put("supplier", appSupplierService.getById(entity.getSupplierId()));
        htmlObj.put("userInfo", appUserService.getById(param.getInteger("userId")));

        translateDictValue(entity);
        try {
            String html = PdfReportUtils.generateHtml("PurchaseOrder.ftl", htmlObj);
            JSONObject obj = new JSONObject();
            obj.put("html", html);
            obj.put("jobName", "进货单_" + entity.getOrderNo());
            result.setData(obj);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AppRunTimeException("html文件导出异常：" + e.getMessage());
        }

        return result;
    }

    @PostMapping(value = "/exportEscp")
    public Result<?> exportEscp(@RequestBody JSONObject param) {
        AppPurchaseOrder entity = JSONObject.toJavaObject(param, AppPurchaseOrder.class);
        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }
        for (AppPurchaseOrderItem item : entity.getItems()) {
            item.setGoodsId(appGoodsService.getTitleById(item.getGoodsId()));
        }
        if (entity.getPaidAmount() == null) entity.setPaidAmount(0.00);
        if (entity.getFreightAmount() == null) entity.setFreightAmount(0.00);
        if (entity.getTotalAmount() == null) entity.setTotalAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(), entity.getPaidAmount()));
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(entity.getTotalAmount())));

        byte[] bytes = EscpReportUtils.purchaseOrder(
                entity,
                appSupplierService.getById(entity.getSupplierId()),
                appUserService.getById(param.getInteger("userId"))
        );
        JSONObject obj = new JSONObject();
        obj.put("data", java.util.Base64.getEncoder().encodeToString(bytes));
        obj.put("jobName", "进货单_" + entity.getOrderNo());
        return Result.ok(obj);
    }

    private Result<?> queryByVoucherOrder(Integer voucherOrderId, JSONObject param){
        if(voucherOrderId == null)
            return Result.ok(new Page<>());

        IPage<AppPurchaseOrder> pageList = new Page<>();
        pageList.setRecords(new ArrayList<>());
        List<AppPaymentAmountItem> paymentAmountItems = paymentAmountItemService.listByOrderId(voucherOrderId.toString());
        paymentAmountItems.forEach(item->{
            AppPurchaseOrder appPurchaseOrder = service.getByOrderNo(item.getOrderNo());
            if(appPurchaseOrder!=null){
                Integer purchaseOrderId = appPurchaseOrder.getId();
                appPurchaseOrder.setAmount(item.getAmount());
                appPurchaseOrder.setNote(item.getNote());
                appPurchaseOrder.setItems(appPurchaseOrderItemService.listByOrderId(purchaseOrderId));
                maskPurchaseOrder(appPurchaseOrder, param);
                appPurchaseOrder.setId(item.getId());
                pageList.getRecords().add(appPurchaseOrder);
            }
        });

        return Result.ok(pageList);
    }

    private void maskPurchaseOrder(AppPurchaseOrder order, JSONObject param) {
        if (order == null) return;
        if (!hasDataViewPermission(param, AppPermissionDefinition.DATA_VIEW_PURCHASE_PRICE)) {
            order.setTotalAmount(0D);
            order.setPayableAmount(0D);
            order.setPaidAmount(0D);
            order.setUnpaidAmount(0D);
            order.setFreightAmount(0D);
            order.setDiscountedAmount(0D);
            order.setAmount(0D);
            if (order.getItems() != null) {
                for (AppPurchaseOrderItem item : order.getItems()) {
                    if (item != null) {
                        item.setUnitPrice(0D);
                        item.setTotalAmount(0D);
                    }
                }
            }
        }
    }

    private void translatePurchaseOrderForExport(AppPurchaseOrder order) {
        if(order == null) return;
        String supplierId = order.getSupplierId();
        String supplierName = getSupplierExportName(supplierId);
        translateDictValue(order);
        if(StringUtils.isNotEmpty(supplierName)){
            order.setSupplierId(supplierName);
        } else if(isUnresolvedNumericDict(supplierId, order.getSupplierId())){
            order.setSupplierId("");
        }
    }

    private String getSupplierExportName(String supplierId) {
        if(StringUtils.isEmpty(supplierId)) return "";
        AppSupplier supplier = appSupplierService.getById(supplierId);
        return supplier == null ? "" : supplier.getName();
    }

    private boolean isUnresolvedNumericDict(String sourceValue, String translatedValue) {
        return StringUtils.isNotEmpty(sourceValue)
                && StringUtils.equals(sourceValue, translatedValue)
                && StringUtils.isNumeric(sourceValue);
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
}
