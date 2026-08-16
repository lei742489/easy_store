package org.jeecgframework.boot.easy_store_boot.app.modules.api.aop;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jeecgframework.boot.easy_store_boot.app.common.dict.DictFieldHandler;
import org.jeecgframework.boot.easy_store_boot.app.common.SymmetricEncoder;
import org.jeecgframework.boot.easy_store_boot.app.exception.ApiNoAuthException;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

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

        checkPermission(request, userId);

        Object result = joinPoint.proceed();
        if (result instanceof Result<?> && !request.getRequestURI().contains("searchKey")) {
            // 处理 Result 类型的返回，填充 data 中字典字段
            result = dictFieldHandler.fillDictFields((Result<?>) result);
        }
        return result;
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
