package org.jeecgframework.boot.easy_store_boot.app.config;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Profile("!dev")
public class AppHomeMenuMigration {

    private final JdbcTemplate jdbcTemplate;

    public AppHomeMenuMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        updateReportMenu("fund_stats", 400);
        updateReportMenu("profit_stats", 401);
        updateReportMenu("cashier_stats", 402);
    }

    private void updateReportMenu(String code, int sortNo) {
        jdbcTemplate.update(
                "UPDATE app_home_menu SET group_code = ?, group_title = ?, sort_no = ? WHERE code = ?",
                "other", "其它功能", sortNo, code
        );
    }
}
