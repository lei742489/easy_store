package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.jeecgframework.boot.easy_store_boot.app.common.PasswordUtil;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("api/user/system")
public class AppSystemController extends ApiBaseController<AppUser, IAppUserService> {

    private static final Set<String> BASIC_TABLES = new HashSet<>(Arrays.asList(
            "app_user",
            "app_home_menu",
            "app_unit",
            "app_customer_category",
            "app_customer_level",
            "app_account_settle_type",
            "app_account_settle",
            "app_income_expense_item"
    ));

    private static final Pattern APP_TABLE_NAME_PATTERN = Pattern.compile("^app_[A-Za-z0-9_]+$");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseDialect databaseDialect;

    @PostMapping("verifyResetDataPassword")
    public Result<?> verifyResetDataPassword(@RequestBody JSONObject param) {
        verifyRootPassword(param);
        return Result.ok();
    }

    @PostMapping("resetData")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> resetData(@RequestBody JSONObject param) {
        verifyRootPassword(param);

        List<String> tables = jdbcTemplate.queryForList(appTablesSql(), String.class);
        List<String> clearedTables = new ArrayList<>();
        for (String table : tables) {
            if (APP_TABLE_NAME_PATTERN.matcher(table).matches() && !BASIC_TABLES.contains(table)) {
                jdbcTemplate.execute("DELETE FROM `" + table + "`");
                clearedTables.add(table);
            }
        }

        jdbcTemplate.update("DELETE FROM app_user WHERE COALESCE(is_root, 0) <> 1");
        jdbcTemplate.update("UPDATE app_account_settle SET cur_prc = COALESCE(init_prc, 0)");
        resetSequences(clearedTables);
        restoreDefaultBusinessData();

        return Result.ok("数据已清零，已保留老板账号和基础配置信息");
    }

    private AppUser verifyRootPassword(JSONObject param) {
        AppUser currentUser = getCurrentUser(param);
        if (currentUser.getIsRoot() == null || currentUser.getIsRoot() != 1) {
            throw new AppRunTimeException("仅Root用户可以执行数据清零");
        }

        String password = param.getString("pwd");
        if (StringUtils.isEmpty(password)) {
            throw new AppRunTimeException("请输入当前老板密码");
        }
        if (StringUtils.isEmpty(currentUser.getSalt())
                || StringUtils.isEmpty(currentUser.getPassword())) {
            throw new AppRunTimeException("老板账号密码未初始化");
        }

        String encodedPassword = PasswordUtil.encrypt(
                currentUser.getUserName(), password, currentUser.getSalt());
        if (!StringUtils.equals(currentUser.getPassword(), encodedPassword)) {
            throw new AppRunTimeException("老板密码验证失败");
        }
        return currentUser;
    }

    private void resetSequences(List<String> clearedTables) {
        List<String> sequenceTables = new ArrayList<>(clearedTables);
        sequenceTables.add("app_user");
        if (databaseDialect.isMySql()) {
            for (String table : sequenceTables) {
                try {
                    jdbcTemplate.execute("ALTER TABLE `" + table + "` AUTO_INCREMENT = 1");
                } catch (Exception ignored) {
                }
            }
            return;
        }
        Integer sequenceTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
                Integer.class);
        if (sequenceTableCount == null || sequenceTableCount == 0 || sequenceTables.isEmpty()) {
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < sequenceTables.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        jdbcTemplate.update(
                "DELETE FROM sqlite_sequence WHERE name IN (" + placeholders + ")",
                sequenceTables.toArray());
    }

    private void restoreDefaultBusinessData() {
        String supplierSql = databaseDialect.isMySql()
                ? "INSERT INTO app_supplier " +
                "(id, name, py_code, status, payable, def_payable, is_del, create_time) " +
                "VALUES (?, ?, ?, 1, 0, 0, 0, " + databaseDialect.currentTimestamp() + ") " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), py_code = VALUES(py_code), " +
                "status = 1, payable = 0, def_payable = 0, is_del = 0, create_time = VALUES(create_time)"
                : "INSERT OR REPLACE INTO app_supplier " +
                "(id, name, py_code, status, payable, def_payable, is_del, create_time) " +
                "VALUES (?, ?, ?, 1, 0, 0, 0, datetime('now', 'localtime'))";
        jdbcTemplate.update(supplierSql,
                1,
                "\u96f6\u6563\u4f9b\u5e94\u5546",
                "lsgys");
        String customerSql = databaseDialect.isMySql()
                ? "INSERT INTO app_customer " +
                "(id, name, py_code, status, discount, payable, def_payable, is_del, create_time) " +
                "VALUES (?, ?, ?, 1, 100, 0, 0, 0, " + databaseDialect.currentTimestamp() + ") " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), py_code = VALUES(py_code), " +
                "status = 1, discount = 100, payable = 0, def_payable = 0, is_del = 0, create_time = VALUES(create_time)"
                : "INSERT OR REPLACE INTO app_customer " +
                "(id, name, py_code, status, discount, payable, def_payable, is_del, create_time) " +
                "VALUES (?, ?, ?, 1, 100, 0, 0, 0, datetime('now', 'localtime'))";
        jdbcTemplate.update(customerSql,
                1,
                "\u96f6\u552e\u5ba2\u6237",
                "lskh");
        String categorySql = databaseDialect.isMySql()
                ? "INSERT INTO app_goods_category " +
                "(id, title, py_code, parent_id, root, is_del, create_time) " +
                "VALUES (?, ?, ?, 0, 0, 0, " + databaseDialect.currentTimestamp() + ") " +
                "ON DUPLICATE KEY UPDATE title = VALUES(title), py_code = VALUES(py_code), " +
                "parent_id = 0, root = 0, is_del = 0, create_time = VALUES(create_time)"
                : "INSERT OR REPLACE INTO app_goods_category " +
                "(id, title, py_code, parent_id, root, is_del, create_time) " +
                "VALUES (?, ?, ?, 0, 0, 0, datetime('now', 'localtime'))";
        jdbcTemplate.update(categorySql,
                1,
                "\u9ed8\u8ba4\u5206\u7c7b",
                "mrfl");
    }

    private String appTablesSql() {
        if (databaseDialect.isMySql()) {
            return "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name LIKE 'app=_%' ESCAPE '='";
        }
        return "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'app_%'";
    }
}
