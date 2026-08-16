package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;


import com.alibaba.fastjson.JSONObject;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentSettleItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentSettleItemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 付款单帐户详情API接口
 */
@RestController
@RequestMapping("api/user/appPaymentSettleItem")
public class AppPaymentSettleItemController extends ApiBaseController<AppPaymentSettleItem, IAppPaymentSettleItemService> {

    @PostMapping("listByOrderId")
    public Result<?> listByOrderId(@RequestBody JSONObject param) {
        return Result.ok(service.listByPaymentId(param.getString("paymentId")));
    }
}
