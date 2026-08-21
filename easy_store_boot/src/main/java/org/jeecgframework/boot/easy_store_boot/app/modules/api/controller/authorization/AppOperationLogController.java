package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppUser;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Root 用户操作日志查询。
 */
@RestController
@RequestMapping("api/user/appOperationLog")
public class AppOperationLogController {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IAppUserService userService;

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        assertRoot(param);

        int current = param.getIntValue("current");
        int pageSize = param.getIntValue("pageSize");
        if (current < 1) current = 1;
        if (pageSize < 1) pageSize = 50;
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        SqlAndParams condition = buildCondition(param);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_operation_log WHERE " + condition.sql,
                Long.class, condition.params.toArray());

        List<Object> pageParams = new ArrayList<>(condition.params);
        pageParams.add(pageSize);
        pageParams.add(offset);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, operator_id AS operatorId, operator_name AS operatorName, " +
                        "menu_name AS menuName, " +
                        "operation_type AS operationType, request_uri AS requestUri, " +
                        "client_ip AS clientIp, operate_time AS operateTime, " +
                        "data_json AS dataJson " +
                        "FROM app_operation_log WHERE " + condition.sql +
                        " ORDER BY operate_time DESC, id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());

        JSONObject result = new JSONObject();
        result.put("records", records);
        result.put("total", total == null ? 0L : total);
        result.put("current", current);
        result.put("pageSize", pageSize);
        return Result.ok(result);
    }

    private void assertRoot(JSONObject param) {
        String userId = param.getString("userId");
        AppUser user = StringUtils.isEmpty(userId) ? null : userService.getById(userId);
        if (user == null || user.getIsRoot() == null || user.getIsRoot() != 1) {
            throw new AppRunTimeException("权限不足");
        }
    }

    private SqlAndParams buildCondition(JSONObject param) {
        StringBuilder sql = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        appendLike(sql, params, "operator_name", param.getString("operatorName"));
        appendLike(sql, params, "menu_name", param.getString("menuName"));
        appendLike(sql, params, "client_ip", param.getString("clientIp"));
        appendEquals(sql, params, "operation_type", param.getString("operationType"));

        String startDate = StringUtils.trimToEmpty(param.getString("startDate"));
        if (StringUtils.isNotEmpty(startDate)) {
            sql.append(" AND operate_time >= ?");
            params.add(toDateTime(startDate, false));
        }
        String endDate = StringUtils.trimToEmpty(param.getString("endDate"));
        if (StringUtils.isNotEmpty(endDate)) {
            sql.append(" AND operate_time < ?");
            params.add(toDateTime(endDate, true));
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private void appendLike(StringBuilder sql, List<Object> params,
                             String column, String value) {
        if (StringUtils.isNotEmpty(StringUtils.trimToEmpty(value))) {
            sql.append(" AND ").append(column).append(" LIKE ?");
            params.add("%" + StringUtils.trim(value) + "%");
        }
    }

    private void appendEquals(StringBuilder sql, List<Object> params,
                               String column, String value) {
        if (StringUtils.isNotEmpty(StringUtils.trimToEmpty(value))) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(StringUtils.trim(value));
        }
    }

    private String toDateTime(String value, boolean nextDay) {
        LocalDate date = LocalDate.parse(value.substring(0, 10), DATE_FORMATTER);
        if (nextDay) {
            date = date.plusDays(1);
        }
        return date + " 00:00:00";
    }

    private static class SqlAndParams {
        private final String sql;
        private final List<Object> params;

        private SqlAndParams(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
