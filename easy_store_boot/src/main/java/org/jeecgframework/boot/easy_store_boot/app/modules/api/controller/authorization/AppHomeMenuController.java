package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.permission.AppPermissionDefinition;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppHomeMenu;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentVoucher;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPurchaseOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppReceivePaymentVoucher;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSaleOrder;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppHomeMenuService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentVoucherService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPurchaseOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppReceivePaymentVoucherService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppRolePermissionService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSaleOrderService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/user/appHomeMenu")
public class AppHomeMenuController {

    @Autowired
    private IAppHomeMenuService homeMenuService;
    @Autowired
    private IAppRolePermissionService rolePermissionService;
    @Autowired
    private IAppUserService userService;
    @Autowired
    private IAppSaleOrderService saleOrderService;
    @Autowired
    private IAppPurchaseOrderService purchaseOrderService;
    @Autowired
    private IAppReceivePaymentVoucherService receivePaymentVoucherService;
    @Autowired
    private IAppPaymentVoucherService paymentVoucherService;

    @PostMapping("listAll")
    public Result<?> listAll(@RequestBody JSONObject param) {
        assertRoot(param);
        List<AppHomeMenu> menus = homeMenuService.list(new LambdaQueryWrapper<AppHomeMenu>()
                .eq(AppHomeMenu::getStatus, 1)
                .orderByAsc(AppHomeMenu::getSortNo));
        return Result.ok(buildPermissionTree(menus));
    }

    @PostMapping("listHome")
    public Result<?> listHome(@RequestBody JSONObject param) {
        AppUser user = userService.getById(param.getInteger("userId"));
        if(user == null) return Result.ok(Collections.emptyList());

        LambdaQueryWrapper<AppHomeMenu> wrapper = new LambdaQueryWrapper<AppHomeMenu>()
                .eq(AppHomeMenu::getStatus, 1)
                .orderByAsc(AppHomeMenu::getSortNo);

        if(user.getIsRoot() == null || user.getIsRoot() != 1){
            List<String> permissionCodes = rolePermissionService.listPermissionCodesByRoleId(user.getRoleId());
            Set<String> visibleMenuCodes = getVisibleMenuCodes(new HashSet<>(permissionCodes));
            if(visibleMenuCodes.isEmpty()) return Result.ok(Collections.emptyList());
            wrapper.eq(AppHomeMenu::getRootOnly, 0);
            wrapper.in(AppHomeMenu::getCode, visibleMenuCodes);
        }

        List<AppHomeMenu> menus = homeMenuService.list(wrapper);
        return Result.ok(groupMenus(menus));
    }

    @PostMapping("pendingApproveCounts")
    public Result<?> pendingApproveCounts(@RequestBody JSONObject param) {
        AppUser user = userService.getById(param.getInteger("userId"));
        if(user == null || user.getIsRoot() == null || user.getIsRoot() != 1){
            return Result.ok(Collections.emptyMap());
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("sale_order_list", saleOrderService.count(new LambdaQueryWrapper<AppSaleOrder>()
                .eq(AppSaleOrder::getStatus, 0)));
        counts.put("purchase_order_list", purchaseOrderService.count(new LambdaQueryWrapper<AppPurchaseOrder>()
                .eq(AppPurchaseOrder::getStatus, 0)));
        counts.put("receive_payment_list", receivePaymentVoucherService.count(new LambdaQueryWrapper<AppReceivePaymentVoucher>()
                .eq(AppReceivePaymentVoucher::getStatus, 0)));
        counts.put("payment_list", paymentVoucherService.count(new LambdaQueryWrapper<AppPaymentVoucher>()
                .eq(AppPaymentVoucher::getStatus, 0)));
        return Result.ok(counts);
    }

    private Set<String> getVisibleMenuCodes(Set<String> permissionCodeSet) {
        Set<String> visibleMenuCodes = new HashSet<>();
        for (String permissionCode : permissionCodeSet) {
            if (permissionCode != null
                    && permissionCode.endsWith(":" + AppPermissionDefinition.ACTION_VIEW)) {
                visibleMenuCodes.add(permissionCode.substring(
                        0, permissionCode.length() - AppPermissionDefinition.ACTION_VIEW.length() - 1));
            }
        }
        if (permissionCodeSet.contains(AppPermissionDefinition.buildCode("sale_order_list", AppPermissionDefinition.ACTION_ADD))) {
            visibleMenuCodes.add("sale_order_add");
        }
        if (permissionCodeSet.contains(AppPermissionDefinition.buildCode("purchase_order_list", AppPermissionDefinition.ACTION_ADD))) {
            visibleMenuCodes.add("purchase_order_add");
        }
        if (permissionCodeSet.contains(AppPermissionDefinition.buildCode("receive_payment_list", AppPermissionDefinition.ACTION_ADD))) {
            visibleMenuCodes.add("receive_payment_add");
        }
        if (permissionCodeSet.contains(AppPermissionDefinition.buildCode("payment_list", AppPermissionDefinition.ACTION_ADD))) {
            visibleMenuCodes.add("payment_add");
        }
        return visibleMenuCodes;
    }

    private List<JSONObject> groupMenus(List<AppHomeMenu> menus) {
        Map<String, List<AppHomeMenu>> groupMap = menus.stream()
                .collect(Collectors.groupingBy(AppHomeMenu::getGroupCode, LinkedHashMap::new, Collectors.toList()));
        List<JSONObject> result = new ArrayList<>();
        for(Map.Entry<String, List<AppHomeMenu>> entry : groupMap.entrySet()){
            List<AppHomeMenu> groupMenus = entry.getValue();
            if(groupMenus.isEmpty()) continue;
            JSONObject group = new JSONObject();
            group.put("code", entry.getKey());
            group.put("title", groupMenus.get(0).getGroupTitle());
            group.put("menus", JSONArray.toJSON(groupMenus));
            result.add(group);
        }
        return result;
    }

    private List<JSONObject> buildPermissionTree(List<AppHomeMenu> menus) {
        Map<String, List<AppHomeMenu>> groupMap = menus.stream()
                .collect(Collectors.groupingBy(AppHomeMenu::getGroupCode, LinkedHashMap::new, Collectors.toList()));
        List<JSONObject> result = new ArrayList<>();
        for (Map.Entry<String, List<AppHomeMenu>> entry : groupMap.entrySet()) {
            List<AppHomeMenu> groupMenus = entry.getValue().stream()
                    .filter(this::isPermissionMenu)
                    .collect(Collectors.toList());
            if (groupMenus.isEmpty()) continue;

            JSONObject group = new JSONObject();
            String groupKey = "group:" + entry.getKey();
            group.put("key", groupKey);
            group.put("title", groupMenus.get(0).getGroupTitle());
            group.put("checkable", false);

            List<JSONObject> menuNodes = new ArrayList<>();
            for (AppHomeMenu menu : groupMenus) {
                JSONObject menuNode = new JSONObject();
                String menuKey = "menu:" + menu.getId();
                menuNode.put("key", menuKey);
                menuNode.put("title", getPermissionMenuTitle(menu.getName()));
                menuNode.put("menuId", menu.getId());
                menuNode.put("menuCode", menu.getCode());

                List<JSONObject> actionNodes = new ArrayList<>();
                for (String action : AppPermissionDefinition.getActions(menu.getCode())) {
                    JSONObject actionNode = new JSONObject();
                    actionNode.put("key", AppPermissionDefinition.buildCode(menu.getCode(), action));
                    actionNode.put("title", getActionTitle(action));
                    actionNode.put("menuId", menu.getId());
                    actionNode.put("menuCode", menu.getCode());
                    actionNode.put("action", action);
                    actionNodes.add(actionNode);
                }
                menuNode.put("children", actionNodes);
                menuNodes.add(menuNode);
            }
            group.put("children", menuNodes);
            result.add(group);
        }
        return result;
    }

    private boolean isPermissionMenu(AppHomeMenu menu) {
        return menu.getUrl() != null
                && !menu.getUrl().trim().isEmpty()
                && !menu.getCode().endsWith("_add");
    }

    private String getPermissionMenuTitle(String title) {
        if (title == null) return "";
        return title.endsWith("查询") ? title.substring(0, title.length() - 2) : title;
    }

    private String getActionTitle(String action) {
        if (AppPermissionDefinition.ACTION_VIEW.equals(action)) return "查看";
        if (AppPermissionDefinition.ACTION_ADD.equals(action)) return "添加";
        if (AppPermissionDefinition.ACTION_EDIT.equals(action)) return "编辑";
        if (AppPermissionDefinition.ACTION_REMOVE.equals(action)) return "删除";
        if (AppPermissionDefinition.ACTION_AUDIT.equals(action)) return "审核";
        return action;
    }

    private void assertRoot(JSONObject param) {
        AppUser user = userService.getById(param.getInteger("userId"));
        if(user == null || user.getIsRoot() == null || user.getIsRoot() != 1){
            throw new AppRunTimeException("只有老板账号可以管理菜单权限");
        }
    }
}
