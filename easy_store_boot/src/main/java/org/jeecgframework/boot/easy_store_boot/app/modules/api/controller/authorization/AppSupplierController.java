package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonConstant;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;


/**
 * 供应商管理API接口
 */
@RestController
@RequestMapping("api/user/appSupplier")
public class AppSupplierController extends ApiBaseController<AppSupplier, IAppSupplierService> {


    @PostMapping("searchKey")
    public Result<?> searchKey(@RequestBody JSONObject param) {
        String key = param.getString("key");
        Set<String> list = new LinkedHashSet<>();
        if(StringUtils.isNotEmpty(key)){

            LambdaQueryWrapper<AppSupplier> wrapper =  new LambdaQueryWrapper<>();
            wrapper.like(AppSupplier::getName, key)
                    .select(AppSupplier::getName)
                    .or().like(AppSupplier::getContactName,key)
                    .or().like(AppSupplier::getMobile,key)
                    .or().like(AppSupplier::getPhone,key)
                    .last("limit " + CommonConstant.AUTO_COMPLETE_MAX_SEARCH_COUNT);

            if(key.matches("^[a-zA-Z]+$"))
                wrapper.or().like(AppSupplier::getPyCode,key);

            List<AppSupplier> dataList= service.list(wrapper);
            if(!dataList.isEmpty()){
                for(AppSupplier appSupplier:dataList){
                    list.add(appSupplier.getName());
                }
            }
        }

        return Result.ok(list);
    }

    /**
     * 列表
     */
    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        String key = param.getString("key");
        LambdaQueryWrapper<AppSupplier> wrapper = new LambdaQueryWrapper<AppSupplier>()
                .eq(AppSupplier::getStatus, 1)
                .orderByAsc(AppSupplier::getId);

        if(StringUtils.isNotEmpty(key)){
            if(key.matches("^[a-zA-Z]+$")){
                wrapper.like(AppSupplier::getPyCode,key);
            }else{
                wrapper.like(AppSupplier::getName,key);
            }

        }
        return Result.ok(service.list(wrapper));
    }

}
