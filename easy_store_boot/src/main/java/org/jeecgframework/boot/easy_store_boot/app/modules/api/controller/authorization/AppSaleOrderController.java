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
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.*;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.*;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * 销售单API接口
 */
@RestController
@RequestMapping("api/user/appSaleOrder")
public class AppSaleOrderController extends ApiBaseController<AppSaleOrder, IAppSaleOrderService> {
    @Autowired
    private IAppSaleOrderItemService appPurchaseOrderItemService;
    @Autowired
    private IAppGoodsService appGoodsService;
    @Autowired
    private TaskSchedulerService taskSchedulerService;
    @Autowired
    private IAppCustomerService appCustomerService;
    @Autowired
    private IAppReceivePaymentAmountItemService receivePaymentAmountItemService;
    @Autowired
    private IAppUserService appUserService;

    @Value("${es-app.path.upload}")
    private String uploadPath;

    private final String  orderTag = "XSD";

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppSaleOrder entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        Integer unpaidOnly = param.getInteger("unpaidOnly");
        String customerId = param.getString("customerId");

        Integer orderId = param.getInteger("orderId");
        if(orderId!=null){
            return queryByVoucherOrder(orderId);
        }

        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;

        String searchKey = param.getString("searchKey");
        if (StringUtils.isNotEmpty(customerId)) {
            entity.setCustomerId(null);
        }
        QueryWrapper<AppSaleOrder> queryWrapper = QueryGenerator.initQueryWrapper(entity,param);
        applyOwnerFilter(queryWrapper, param);
        if (StringUtils.isNotEmpty(customerId)) {
            queryWrapper.eq("customer_id", customerId);
        }

        if(unpaidOnly!=null){
            queryWrapper.ne("unpaid_amount",0).eq("status", 1);
        }


        if(StringUtils.isNotEmpty(searchKey)){
            queryWrapper.and(w -> w.like("order_no",searchKey).or().like("note",searchKey));
        }
        queryWrapper.orderByDesc("id");
        Page<AppSaleOrder> page = new Page<>(current, pageSize);
        IPage<AppSaleOrder> pageList = service.page(page, queryWrapper);
        boolean rootUser = isRootUser(param);
        for(AppSaleOrder appPurchaseOrder : pageList.getRecords()){
            appPurchaseOrder.setItems(appPurchaseOrderItemService.listByOrderId(appPurchaseOrder.getId()));
            if(!rootUser){
                hideGrossProfit(appPurchaseOrder);
            }
        }
        return Result.ok(pageList);
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
            AppSaleOrder order = service.getById(id);
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
    public ModelAndView exportXls(HttpServletRequest request, AppSaleOrder object,  String title) {
        // 过滤选中数据
        String selections = request.getParameter("selections");
        List<String> ids = Arrays.asList(selections.split(","));
        boolean rootUser = isRootRequest(request);
        LambdaQueryWrapper<AppSaleOrder> wrapper = new LambdaQueryWrapper<AppSaleOrder>()
                .in(AppSaleOrder::getId, ids)
                .orderByDesc(AppSaleOrder::getCreateTime);
        if(!rootUser){
            wrapper.eq(AppSaleOrder::getCashierId, request.getParameter("userId"));
        }
        List<AppSaleOrder> list = service.list(wrapper);

        for(AppSaleOrder order : list){
            translateSaleOrderForExport(order);
            order.setItems(appPurchaseOrderItemService.listByOrderId(order.getId()));
            if(!rootUser){
                hideGrossProfit(order);
            }
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
            List<AppSaleOrder> list = ExcelImportUtil.importExcel(is, getEntityClass(), params);
            if(list.size()>1000)
                throw new AppRunTimeException("单次导入不能超过1000条数据");
            int idx = 0;
            for (AppSaleOrder appPurchaseOrder : list) {
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
        AppSaleOrder entity = JSONObject.toJavaObject(param, AppSaleOrder.class);
        Result<Object> result = new Result<>();

        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }


        for (AppSaleOrderItem item : entity.getItems()) {
            item.setGoodsId(appGoodsService.getTitleById(item.getGoodsId()));
        }

        if (entity.getPaidAmount() == null)
            entity.setPaidAmount(0.00);

        if(entity.getFreightAmount()==null)entity.setFreightAmount(0.00);
         entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(), entity.getPaidAmount()));
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(entity.getTotalAmount())));


        JSONObject pdfObj = new JSONObject();
        pdfObj.putIfAbsent("obj",entity);
        pdfObj.put("customer",appCustomerService.getById(entity.getCustomerId()));
        pdfObj.put("userInfo",appUserService.getById(param.getInteger("userId")));
        translateDictValue(entity);

        try {
            String subPath = "/report/销售单_" + System.currentTimeMillis() + ".pdf";
            String html = PdfReportUtils.generateHtml("SaleOrder.ftl",pdfObj );
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

    @PostMapping(value = "/exportEscp")
    public Result<?> exportEscp(@RequestBody JSONObject param) {
        AppSaleOrder entity = JSONObject.toJavaObject(param, AppSaleOrder.class);
        if (entity == null || entity.getItems() == null || entity.getItems().isEmpty()) {
            throw new AppRunTimeException("数据输入不完整，请检查");
        }
        for (AppSaleOrderItem item : entity.getItems()) {
            item.setGoodsId(appGoodsService.getTitleById(item.getGoodsId()));
        }
        if (entity.getPaidAmount() == null) entity.setPaidAmount(0.00);
        if (entity.getFreightAmount() == null) entity.setFreightAmount(0.00);
        if (entity.getTotalAmount() == null) entity.setTotalAmount(0.00);
        entity.setUnpaidAmount(DoubleUtil.sub(entity.getPayableAmount(), entity.getPaidAmount()));
        entity.setTotalAmountChinese(AmountToChineseUtil.toChinese(BigDecimal.valueOf(entity.getTotalAmount())));

        byte[] bytes = EscpReportUtils.saleOrder(
                entity,
                appCustomerService.getById(entity.getCustomerId()),
                appUserService.getById(param.getInteger("userId"))
        );
        JSONObject obj = new JSONObject();
        obj.put("data", java.util.Base64.getEncoder().encodeToString(bytes));
        obj.put("jobName", "销售单_" + entity.getOrderNo());
        return Result.ok(obj);
    }


    private Result<?> queryByVoucherOrder(Integer voucherOrderId){
        if(voucherOrderId == null)
            return Result.ok(new Page<>());

        IPage<AppSaleOrder> pageList = new Page<>();
        pageList.setRecords(new ArrayList<>());
        List<AppReceivePaymentAmountItem> paymentAmountItems = receivePaymentAmountItemService.listByOrderId(voucherOrderId.toString());
        paymentAmountItems.forEach(item->{
            AppSaleOrder saleOrder = service.getByOrderNo(item.getOrderNo());
            if(saleOrder!=null){
                saleOrder.setAmount(item.getAmount());
                saleOrder.setNote(item.getNote());
                saleOrder.setId(item.getId());
                pageList.getRecords().add(saleOrder);
            }
        });
        return Result.ok(pageList);
    }

    private void translateSaleOrderForExport(AppSaleOrder order) {
        if(order == null) return;
        String customerId = order.getCustomerId();
        String customerName = getCustomerExportName(customerId);
        translateDictValue(order);
        if(StringUtils.isNotEmpty(customerName)){
            order.setCustomerId(customerName);
        } else if(isUnresolvedNumericDict(customerId, order.getCustomerId())){
            order.setCustomerId("");
        }
    }

    private String getCustomerExportName(String customerId) {
        if(StringUtils.isEmpty(customerId)) return "";
        AppCustomer customer = appCustomerService.getById(customerId);
        if(customer != null && StringUtils.isNotEmpty(customer.getName())){
            return customer.getName();
        }
        if("0".equals(customerId)){
            AppCustomer retailCustomer = appCustomerService.getOne(
                    new LambdaQueryWrapper<AppCustomer>().eq(AppCustomer::getName, "零售客户").last("LIMIT 1"),
                    false
            );
            return retailCustomer == null ? "零售客户" : retailCustomer.getName();
        }
        return "";
    }

    private boolean isUnresolvedNumericDict(String sourceValue, String translatedValue) {
        return StringUtils.isNotEmpty(sourceValue)
                && StringUtils.equals(sourceValue, translatedValue)
                && StringUtils.isNumeric(sourceValue);
    }

    private void hideGrossProfit(AppSaleOrder order) {
        if(order == null) return;
        order.setGrossProfit(null);
        if(order.getItems() == null) return;
        for(AppSaleOrderItem item : order.getItems()){
            item.setGrossProfit(null);
        }
    }

    private boolean isRootRequest(HttpServletRequest request) {
        String userId = request.getParameter("userId");
        if(StringUtils.isEmpty(userId)) {
            return false;
        }
        AppUser user = appUserService.getById(userId);
        return user != null && user.getIsRoot() != null && user.getIsRoot() == 1;
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
