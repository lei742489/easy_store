package org.jeecgframework.boot.easy_store_boot.app.modules.api.aop;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictFieldHandler;
import org.jeecgframework.boot.easy_store_boot.app.common.http.IPUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.SymmetricEncoder;
import org.jeecgframework.boot.easy_store_boot.app.exception.ApiNoAuthException;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppOperationLogService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 用户类接口切面类，处理判断登录状态，封装字典，token等数据
 */
@Aspect
@Component
@Slf4j
public class ApiAop {
    @Autowired
    private DictFieldHandler dictFieldHandler;
    @Autowired
    private IAppUserService appUserService;
    @Autowired
    private IAppRolePermissionService rolePermissionService;
    @Autowired
    private IAppOperationLogService operationLogService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 定义切点
    @Pointcut("(execution(public * org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization..*(..)) " +
            "|| execution(public * org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController.*(..))) " +
            "&& @annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void appApiUserControllerMethods() {}


    @Around("appApiUserControllerMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();


        String userId = null;
        try {
             userId = SymmetricEncoder.tokenToUserId(request.getHeader("token"));
            if(userId ==  null){
                throw new ApiNoAuthException("登录过期，请重新登录");
            }
        }catch (Exception e){
            throw new ApiNoAuthException(e.getMessage());
        }

        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if(arg instanceof JSONObject){
                    ((JSONObject) arg).put("userId", userId);
                    assert request != null;
                    String token = request.getHeader("token");
                    ((JSONObject) arg).put("token", token);
                }

            }
        }

        AppUser operator = appUserService.getById(userId);
        if (operator == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        String operationType = getOperationType(getLastPath(request.getRequestURI()));

        checkPermission(request, userId);

        Object result = joinPoint.proceed();
        if (!(result instanceof Result) || ((Result<?>) result).isSuccess()) {
            recordOperationLog(request, userId, operator, operationType, args);
        }
        if (result instanceof Result<?> && !request.getRequestURI().contains("searchKey")) {
            // 处理 Result 类型的返回，填充 data 中字典字段
            result = dictFieldHandler.fillDictFields((Result<?>) result);
        }
        return result;
    }

    private void recordOperationLog(HttpServletRequest request, String userId,
                                    AppUser operator, String operationType, Object[] args) {
        if (operationType == null) {
            return;
        }
        try {
            String operatorName = org.apache.commons.lang.StringUtils.isNotEmpty(operator.getRealName())
                    ? operator.getRealName() : operator.getUserName();
            operationLogService.record(userId, operatorName,
                    getMenuName(request.getRequestURI()), operationType,
                    request.getRequestURI(), IPUtil.getIpAddress(request),
                    buildOperationData(args));
        } catch (Exception e) {
            log.warn("记录操作日志失败，不影响原业务请求: {}", request.getRequestURI(), e);
        }
    }

    private String getMenuName(String requestUri) {
        String menuCode = AppPermissionDefinition.getMenuCodeByRequestUri(requestUri);
        if (menuCode == null) {
            return "";
        }
        try {
            String menuName = jdbcTemplate.queryForObject(
                    "SELECT name FROM app_home_menu WHERE code = ? " +
                            "AND COALESCE(status, 1) = 1 AND COALESCE(is_del, 0) = 0 LIMIT 1",
                    String.class, menuCode);
            return menuName == null ? "" : menuName;
        } catch (Exception e) {
            log.warn("读取操作日志菜单名称失败: {}", menuCode, e);
            return "";
        }
    }

    private String buildOperationData(Object[] args) {
        JSONObject data = new JSONObject();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof JSONObject) {
                    JSONObject object = (JSONObject) sanitizeValue(arg);
                    for (Map.Entry<String, Object> entry : object.entrySet()) {
                        data.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return data.toJSONString();
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject target = new JSONObject();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (!isSensitiveKey(entry.getKey())) {
                    target.put(entry.getKey(), sanitizeValue(entry.getValue()));
                }
            }
            return target;
        }
        if (value instanceof JSONArray) {
            JSONArray target = new JSONArray();
            for (Object item : (JSONArray) value) {
                target.add(sanitizeValue(item));
            }
            return target;
        }
        if (value instanceof Map) {
            JSONObject target = new JSONObject();
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                String key = String.valueOf(entry.getKey());
                if (!isSensitiveKey(key)) {
                    target.put(key, sanitizeValue(entry.getValue()));
                }
            }
            return target;
        }
        if (value instanceof Collection) {
            JSONArray target = new JSONArray();
            for (Object item : (Collection<?>) value) {
                target.add(sanitizeValue(item));
            }
            return target;
        }
        if (value != null && value.getClass().isArray()) {
            JSONArray target = new JSONArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                target.add(sanitizeValue(Array.get(value, i)));
            }
            return target;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase();
        return "token".equals(normalized)
                || "password".equals(normalized)
                || "pwd".equals(normalized)
                || normalized.contains("password");
    }

    private String getOperationType(String methodPath) {
        if (methodPath == null) return null;
        String path = methodPath.toLowerCase();
        if ("add".equals(path) || path.endsWith("add") || "importexcel".equals(path)) {
            return AppPermissionDefinition.ACTION_ADD;
        }
        if ("edit".equals(path) || path.endsWith("edit")
                || path.startsWith("approve") || path.startsWith("batchupdate")
                || path.startsWith("batchchange") || "updatepwd".equals(path)
                || "resetpwd".equals(path)) {
            return AppPermissionDefinition.ACTION_EDIT;
        }
        if ("remove".equals(path) || path.endsWith("remove")
                || path.startsWith("batchremove") || "resetdata".equals(path)) {
            return AppPermissionDefinition.ACTION_REMOVE;
        }
        return null;
    }

    private void checkPermission(HttpServletRequest request, String userId) {
        String action = AppPermissionDefinition.getActionByMethodPath(getLastPath(request.getRequestURI()));
        if (action == null) return;

        AppUser user = appUserService.getById(userId);
        if (user == null) {
            throw new AppRunTimeException("用户数据不存在，请重新登录");
        }
        if (user.getIsRoot() != null && user.getIsRoot() == 1) {
            return;
        }

        String menuCode = AppPermissionDefinition.getMenuCodeByRequestUri(request.getRequestURI());
        if (menuCode == null) return;

        String permissionCode = AppPermissionDefinition.buildCode(menuCode, action);
        if (!rolePermissionService.hasPermission(user.getRoleId(), permissionCode)) {
            throw new AppRunTimeException("权限不足");
        }
    }

    private String getLastPath(String requestUri) {
        if (requestUri == null) return null;
        int index = requestUri.lastIndexOf("/");
        return index >= 0 ? requestUri.substring(index + 1) : requestUri;
    }



}
