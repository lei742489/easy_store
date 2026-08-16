package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;


import com.alibaba.fastjson.JSONObject;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppPaymentSettleItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppReceivePaymentSettleItem;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppPaymentSettleItemService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppReceivePaymentSettleItemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收款单帐户详情API接口
 */
@RestController
@RequestMapping("api/user/appReceivePaymentSettleItem")
public class AppReceivePaymentSettleItemController extends ApiBaseController<AppReceivePaymentSettleItem, IAppReceivePaymentSettleItemService> {

    @PostMapping("listByOrderId")
    public Result<?> listByOrderId(@RequestBody JSONObject param) {
        return Result.ok(service.listByPaymentId(param.getString("paymentId")));
    }
}
