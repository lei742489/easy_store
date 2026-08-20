package org.jeecgframework.boot.easy_store_boot.app.modules.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DatabaseDialect;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppOperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现。
 */
@Service
@Slf4j
public class AppOperationLogServiceImpl implements IAppOperationLogService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseDialect databaseDialect;

    @Override
    public void record(String operatorId, String operatorName, String menuName,
                       String operationType, String requestUri, String clientIp,
                       String dataJson) {
        jdbcTemplate.update("INSERT INTO app_operation_log " +
                        "(operator_id, operator_name, menu_name, operation_type, request_uri, " +
                        "client_ip, operate_time, data_json) " +
                        "VALUES (?, ?, ?, ?, ?, ?, " + databaseDialect.currentTimestamp() + ", ?)",
                StringUtils.defaultString(operatorId),
                StringUtils.defaultString(operatorName),
                StringUtils.defaultString(menuName),
                StringUtils.defaultString(operationType),
                StringUtils.defaultString(requestUri),
                StringUtils.defaultString(clientIp),
                StringUtils.defaultString(dataJson, "{}"));
    }

    @Override
    public void cleanupExpired() {
        try {
            jdbcTemplate.update("DELETE FROM app_operation_log " +
                    "WHERE operate_time < " + databaseDialect.currentTimestampMinusMonths(3));
        } catch (Exception e) {
            log.warn("清理三个月前操作日志失败", e);
        }
    }
}
