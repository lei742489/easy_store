package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.DateUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppStockCheck;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockCheckItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppStockCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user/appStockCheck")
public class AppStockCheckController extends ApiBaseController<AppStockCheck, IAppStockCheckService> {

    private static final String ORDER_TAG = "PDD";

    @Autowired
    private IAppStockCheckItemService stockCheckItemService;

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppStockCheck entity = JSONObject.toJavaObject(param, getEntityClass());
        int current = param.getInteger("current") == null ? 1 : param.getInteger("current");
        int pageSize = param.getInteger("pageSize") == null ? 15 : param.getInteger("pageSize");
        String searchKey = param.getString("searchKey");

        QueryWrapper<AppStockCheck> queryWrapper = QueryGenerator.initQueryWrapper(entity, param);
        applyOwnerFilter(queryWrapper, param);
        if (StringUtils.isNotEmpty(searchKey)) {
            queryWrapper.and(wrapper -> wrapper.like("order_no", searchKey)
                    .or().like("note", searchKey));
        }
        queryWrapper.orderByDesc("id");
        IPage<AppStockCheck> page = service.page(new Page<>(current, pageSize), queryWrapper);
        for (AppStockCheck stockCheck : page.getRecords()) {
            stockCheck.setItems(stockCheckItemService.listByCheckId(stockCheck.getId()));
        }
        return Result.ok(page);
    }

    @PostMapping("createOrderNo")
    public Result<?> createOrderNo(@RequestBody JSONObject param) {
        Result<String> result = new Result<>();
        result.setData(ORDER_TAG + DateUtils.formatOrder(null));
        return result;
    }
}
