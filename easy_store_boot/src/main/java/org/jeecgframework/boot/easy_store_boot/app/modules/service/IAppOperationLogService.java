package org.jeecgframework.boot.easy_store_boot.app.modules.service;

/**
 * 操作日志服务。
 */
public interface IAppOperationLogService {

    void record(String operatorId, String operatorName, String menuName,
                String operationType, String requestUri, String clientIp,
                String dataJson);

    void cleanupExpired();
}
