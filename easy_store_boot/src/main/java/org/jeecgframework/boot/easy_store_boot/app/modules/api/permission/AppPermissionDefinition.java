package org.jeecgframework.boot.easy_store_boot.app.modules.api.permission;

import java.util.*;

public final class AppPermissionDefinition {

    public static final String ACTION_VIEW = "view";
    public static final String ACTION_ADD = "add";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_REMOVE = "remove";
    public static final String ACTION_AUDIT = "audit";
    public static final String DATA_VIEW_COST_PRICE = "data_view:cost_price";
    public static final String DATA_VIEW_PURCHASE_PRICE = "data_view:purchase_price";
    public static final String DATA_VIEW_TRADE_PRICE = "data_view:trade_price";
    public static final String DATA_VIEW_SALE_PRICE = "data_view:sale_price";

    private static final Map<String, String> API_MENU_CODE_MAP = new LinkedHashMap<>();

    static {
        API_MENU_CODE_MAP.put("/api/user/appSaleOrder", "sale_order_list");
        API_MENU_CODE_MAP.put("/api/user/appReceivePaymentVoucher", "receive_payment_list");
        API_MENU_CODE_MAP.put("/api/user/appReceivePaymentSettleItem", "receive_payment_list");
        API_MENU_CODE_MAP.put("/api/user/appPurchaseOrder", "purchase_order_list");
        API_MENU_CODE_MAP.put("/api/user/appStockCheck", "stock_check");
        API_MENU_CODE_MAP.put("/api/user/appPaymentVoucher", "payment_list");
        API_MENU_CODE_MAP.put("/api/user/appPaymentSettleItem", "payment_list");
        API_MENU_CODE_MAP.put("/api/user/appGoods", "goods");
        API_MENU_CODE_MAP.put("/api/user/goods/category", "goods");
        API_MENU_CODE_MAP.put("/api/user/customer", "customer");
        API_MENU_CODE_MAP.put("/api/user/customer/category", "customer");
        API_MENU_CODE_MAP.put("/api/user/customer/level", "customer");
        API_MENU_CODE_MAP.put("/api/user/appCustomerQuote", "customer_quote");
        API_MENU_CODE_MAP.put("/api/user/appSupplier", "supplier");
        API_MENU_CODE_MAP.put("/api/user/appAccountSettle", "account_settle");
        API_MENU_CODE_MAP.put("/api/user/appFundStatistics", "fund_stats");
        API_MENU_CODE_MAP.put("/api/user/appIncomeExpenseRecord", "income_expense_record");
        API_MENU_CODE_MAP.put("/api/user/appUnit", "app_unit");
        API_MENU_CODE_MAP.put("/api/user/appRole", "app_role");
        API_MENU_CODE_MAP.put("/api/user", "app_user");
    }

    private AppPermissionDefinition() {
    }

    public static String buildCode(String menuCode, String action) {
        return menuCode + ":" + action;
    }

    public static boolean isAuditMenu(String menuCode) {
        return "sale_order_list".equals(menuCode)
                || "purchase_order_list".equals(menuCode)
                || "receive_payment_list".equals(menuCode)
                || "payment_list".equals(menuCode);
    }

    public static String getMenuCodeByEntityClass(Class<?> entityClass) {
        if (entityClass == null) return null;
        String simpleName = entityClass.getSimpleName();
        if ("AppSaleOrder".equals(simpleName)) return "sale_order_list";
        if ("AppPurchaseOrder".equals(simpleName)) return "purchase_order_list";
        if ("AppReceivePaymentVoucher".equals(simpleName)) return "receive_payment_list";
        if ("AppPaymentVoucher".equals(simpleName)) return "payment_list";
        return null;
    }

    public static String getMenuCodeByRequestUri(String requestUri) {
        if (requestUri == null) return null;
        String matched = null;
        for (String apiPrefix : API_MENU_CODE_MAP.keySet()) {
            if (requestUri.startsWith(apiPrefix) && (matched == null || apiPrefix.length() > matched.length())) {
                matched = apiPrefix;
            }
        }
        return matched == null ? null : API_MENU_CODE_MAP.get(matched);
    }

    public static String getActionByMethodPath(String methodPath) {
        if (methodPath == null) return null;
        String path = methodPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if ("add".equals(path)) {
            return ACTION_ADD;
        }
        if (path.startsWith("approve")) {
            return ACTION_AUDIT;
        }
        if ("edit".equals(path) || path.startsWith("batchUpdate") || path.startsWith("batchChange")) {
            return ACTION_EDIT;
        }
        if ("remove".equals(path) || path.startsWith("batchRemove")) {
            return ACTION_REMOVE;
        }
        return null;
    }

    public static List<String> getActions() {
        return Arrays.asList(ACTION_VIEW, ACTION_ADD, ACTION_EDIT, ACTION_REMOVE);
    }

    public static List<String> getActions(String menuCode) {
        List<String> actions = new ArrayList<>(getActions());
        if (isAuditMenu(menuCode)) {
            actions.add(ACTION_AUDIT);
        }
        return actions;
    }

    public static List<String> getDataViewPermissions() {
        return Arrays.asList(
                DATA_VIEW_COST_PRICE,
                DATA_VIEW_PURCHASE_PRICE,
                DATA_VIEW_TRADE_PRICE,
                DATA_VIEW_SALE_PRICE
        );
    }
}
