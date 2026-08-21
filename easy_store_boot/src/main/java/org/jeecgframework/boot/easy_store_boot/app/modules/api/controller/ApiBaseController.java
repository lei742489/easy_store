package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import io.swagger.annotations.ApiModel;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.jeecgframework.boot.easy_store_boot.app.common.DateUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.CommonDictMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiBaseController<T, S extends IService<T>> {

    @Autowired
    protected S service;
    @Autowired
    private CommonDictMapper commonDictMapper;
    @Autowired
    protected IAppUserService appUserService;
    @Autowired
    protected IAppRolePermissionService appRolePermissionService;

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        T entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;

        QueryWrapper<T> queryWrapper = QueryGenerator.initQueryWrapper(entity,param);
        applyOwnerFilter(queryWrapper, param);
        queryWrapper.orderByDesc("id");
        Page<T> page = new Page<>(current, pageSize);
        IPage<T> pageList = service.page(page, queryWrapper);
        return Result.ok(pageList);
    }

    @PostMapping("add")
    public Result<?> add(@RequestBody JSONObject param) {
        T entity = JSONObject.toJavaObject(param, getEntityClass());
        prepareDocumentForAdd(entity, param);
        boolean res = service.save(entity);
        if (!res) throw new AppRunTimeException("添加失败");
        return Result.ok();
    }

    @PostMapping("edit")
    public Result<?> edit(@RequestBody JSONObject param) {
        T entity = JSONObject.toJavaObject(param, getEntityClass());
        prepareDocumentForEdit(entity, param);
        boolean res = service.updateById(entity);
        if (!res) throw new AppRunTimeException("更新失败");
        return Result.ok();
    }

    @PostMapping("remove")
    public Result<?> remove(@RequestBody JSONObject param) {
        String id = param.getString("id");
        assertDocumentOwner(id, param);
        boolean res = service.removeById(id);
        if (!res) throw new AppRunTimeException("删除失败");
        return Result.ok();
    }

    /**
     * 导出excel
     *
     * @param request
     */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, T object,  String title) {
       return   doExcelExport(request, object, title);
    }

    protected ModelAndView doExcelExport(HttpServletRequest request, T object, String title) {
        // Step.1 组装查询条件
        QueryWrapper<T> queryWrapper = QueryGenerator.initQueryWrapper(object, request.getParameterMap());

        // Step.2 获取导出数据
        List<T> pageList = service.list(queryWrapper);
        List<T> exportList = null;

        // 过滤选中数据
        String selections = request.getParameter("selections");
        if (StringUtils.isNotEmpty(selections)) {
            List<String> selectionList = Arrays.asList(selections.split(","));
            exportList = pageList.stream().filter(item -> selectionList.contains(getId(item))).collect(Collectors.toList());
        } else {
            exportList = pageList;
        }

        for(T item : exportList) {
            translateDictValue(item) ;
        }
        // Step 4：AutoPoi 导出
        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        ExportParams exportParams = new ExportParams(title, "", title);
        exportParams.setType(ExcelType.XSSF); // .xlsx 类型

        ApiModel apiModel = getEntityClass().getAnnotation(ApiModel.class);

        mv.addObject(NormalExcelConstants.FILE_NAME, apiModel.description() + "_" + System.currentTimeMillis()); // 文件名
        mv.addObject(NormalExcelConstants.CLASS, getEntityClass());     // 数据类型
        mv.addObject(NormalExcelConstants.PARAMS, exportParams); // 导出参数
        mv.addObject(NormalExcelConstants.DATA_LIST, exportList); // 数据

        return mv;
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
            List<T> list = ExcelImportUtil.importExcel(is, getEntityClass(), params);
            service.saveBatch(list);
            return Result.ok("文件导入成功！数据行数：" + list.size());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文件导入失败:" + e.getMessage());
        }
    }

    /**
     * 获取泛型实体类型（用于反序列化）
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getEntityClass() {
        return (Class<T>) ((java.lang.reflect.ParameterizedType)
            this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    /**
     * 获取对象ID
     *
     * @return
     */
    private String getId(T item) {
        try {
            return PropertyUtils.getProperty(item, "id").toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected void translateDictValue(T obj) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            Excel excel = field.getAnnotation(Excel.class);
            if (excel != null && !excel.dictTable().isEmpty() && !excel.dicText().isEmpty() && !excel.dicCode().isEmpty()) {

                try {
                    field.setAccessible(true);
                    Object codeValue = field.get(obj);

                    if (codeValue == null) continue;

                    // 查询字典文本
                    String dictText = commonDictMapper.selectDictText(
                            excel.dictTable(),
                            excel.dicText(),
                            excel.dicCode(),
                            codeValue);

                    if (dictText != null) {
                        // 直接覆盖字段的值
                        if (field.getType() == String.class) {
                            field.set(obj, dictText);
                        } else {
                            // 如果字段不是String类型，转成对应类型后设置（一般字典字段都是字符串或整数）
                            // 简单示例，只处理String类型，其他类型请根据需要扩展
                            field.set(obj, dictText);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected AppUser getCurrentUser(JSONObject param) {
        if (param == null || StringUtils.isEmpty(param.getString("userId"))) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        AppUser user = appUserService.getById(param.getString("userId"));
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        return user;
    }

    protected boolean isRootUser(JSONObject param) {
        return isRoot(getCurrentUser(param));
    }

    protected void prepareDocumentForAdd(T entity, JSONObject param) {
        AppUser user = getCurrentUser(param);
        if (!hasField(entity, "cashierId")) {
            return;
        }
        fillCashierInfo(entity, user, param.getString("userId"));
        setDocumentStatus(entity, user, null);
    }

    protected void prepareDocumentForEdit(T entity, JSONObject param) {
        AppUser user = getCurrentUser(param);
        if (!hasField(entity, "cashierId")) {
            return;
        }
        T dbEntity = getEntityById(entity);
        if (dbEntity == null) {
            throw new AppRunTimeException("单据不存在或已删除");
        }
        String userId = param.getString("userId");
        if (!isRoot(user) && !StringUtils.equals(getFieldValue(dbEntity, "cashierId"), userId)) {
            throw new AppRunTimeException("只能操作本人单据");
        }
        if (!isRoot(user)) {
            copyField(dbEntity, entity, "cashierId");
            copyField(dbEntity, entity, "cashierName");
        } else {
            fillCashierInfo(entity, user, userId);
        }
        setDocumentStatus(entity, user, getFieldValue(dbEntity, "status"));
        setDocumentUpdateBy(entity, user);
    }

    protected void setDocumentUpdateBy(T entity, JSONObject param) {
        setDocumentUpdateBy(entity, getCurrentUser(param));
    }

    protected void setDocumentUpdateBy(T entity, AppUser user) {
        if (entity == null || user == null || !hasField(entity, "updateBy")) {
            return;
        }
        try {
            Field field = entity.getClass().getDeclaredField("updateBy");
            field.setAccessible(true);
            field.set(entity, getUserDisplayName(user));
        } catch (Exception e) {
            throw new AppRunTimeException("设置最后更新人失败");
        }
    }

    protected void assertDocumentOwner(String id, JSONObject param) {
        AppUser user = getCurrentUser(param);
        T dbEntity = service.getById(id);
        if (dbEntity == null || !hasField(dbEntity, "cashierId") || isRoot(user)) {
            return;
        }
        if (!StringUtils.equals(getFieldValue(dbEntity, "cashierId"), param.getString("userId"))) {
            throw new AppRunTimeException("只能操作本人单据");
        }
    }

    protected void applyOwnerFilter(QueryWrapper<T> queryWrapper, JSONObject param) {
        T entity = JSONObject.toJavaObject(param, getEntityClass());
        if (queryWrapper == null || !hasField(entity, "cashierId") || isRootUser(param)) {
            return;
        }
        queryWrapper.eq("cashier_id", param.getString("userId"));
    }

    private void setDocumentStatus(T entity, AppUser user, String oldStatus) {
        if (!hasField(entity, "status")) {
            return;
        }
        try {
            Field statusField = entity.getClass().getDeclaredField("status");
            statusField.setAccessible(true);
            if (!isRoot(user)) {
                statusField.set(entity, shouldAuditDocument(entity, user) ? 0 : 1);
            } else if (statusField.get(entity) == null) {
                statusField.set(entity, oldStatus == null ? 1 : Integer.valueOf(oldStatus));
            }
        } catch (Exception e) {
            throw new AppRunTimeException("设置单据状态失败");
        }
    }

    private boolean shouldAuditDocument(T entity, AppUser user) {
        String menuCode = AppPermissionDefinition.getMenuCodeByEntityClass(entity.getClass());
        if (StringUtils.isEmpty(menuCode)) {
            return false;
        }
        return appRolePermissionService.hasPermission(
                user.getRoleId(),
                AppPermissionDefinition.buildCode(menuCode, AppPermissionDefinition.ACTION_AUDIT));
    }

    private void fillCashierInfo(T entity, AppUser user, String userId) {
        if (entity == null || StringUtils.isEmpty(userId)) return;
        try {
            Field cashierIdField = entity.getClass().getDeclaredField("cashierId");
            Field cashierNameField = entity.getClass().getDeclaredField("cashierName");
            if (!isRoot(user)) {
                cashierIdField.setAccessible(true);
                cashierIdField.set(entity, userId);
                cashierNameField.setAccessible(true);
                cashierNameField.set(entity, getUserDisplayName(user));
                return;
            }

            cashierIdField.setAccessible(true);
            Object cashierId = cashierIdField.get(entity);
            if (cashierId != null && StringUtils.isNotEmpty(cashierId.toString())) {
                AppUser cashier = appUserService.getById(cashierId.toString());
                cashierNameField.setAccessible(true);
                cashierNameField.set(entity, getUserDisplayName(cashier));
            }
        } catch (NoSuchFieldException ignored) {
        } catch (Exception e) {
            throw new AppRunTimeException("设置营业员失败");
        }
    }

    private boolean hasField(T entity, String fieldName) {
        if (entity == null) {
            return false;
        }
        try {
            entity.getClass().getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private T getEntityById(T entity) {
        try {
            Object id = PropertyUtils.getProperty(entity, "id");
            return id == null ? null : service.getById((Serializable) id);
        } catch (Exception e) {
            throw new AppRunTimeException("读取单据失败");
        }
    }

    private String getFieldValue(T entity, String fieldName) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void copyField(T source, T target, String fieldName) {
        try {
            Field field = source.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, field.get(source));
        } catch (Exception e) {
            throw new AppRunTimeException("设置单据归属失败");
        }
    }

    private boolean isRoot(AppUser user) {
        return user != null && user.getIsRoot() != null && user.getIsRoot() == 1;
    }

    private String getUserDisplayName(AppUser user) {
        if (user == null) return "";
        if (StringUtils.isNotEmpty(user.getRealName())) return user.getRealName();
        return user.getUserName();
    }


}
