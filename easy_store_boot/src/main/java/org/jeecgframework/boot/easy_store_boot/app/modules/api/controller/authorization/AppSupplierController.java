package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonConstant;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
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


    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppSupplier entity = JSONObject.toJavaObject(param, AppSupplier.class);
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;
        String key = param.getString("key");

        QueryWrapper<AppSupplier> queryWrapper = QueryGenerator.initQueryWrapper(entity, param);
        if (StringUtils.isNotEmpty(key)) {
            queryWrapper.and(wrapper -> wrapper.like("name", key)
                    .or().like("contact_name", key)
                    .or().like("mobile", key)
                    .or().like("phone", key)
                    .or().like("py_code", key));
        }

        Page<AppSupplier> page = new Page<>(current, pageSize);
        IPage<AppSupplier> pageList = service.page(page, queryWrapper);
        return Result.ok(pageList);
    }

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

    @PostMapping("defaultOne")
    public Result<?> defaultOne() {
        AppSupplier supplier = service.getOne(new LambdaQueryWrapper<AppSupplier>()
                .eq(AppSupplier::getStatus, 1)
                .orderByAsc(AppSupplier::getId)
                .last("limit 1"));
        return Result.ok(supplier);
    }

    @PostMapping("refreshPayable")
    public Result<?> refreshPayable() {
        return Result.ok(service.refreshAllPayable());
    }

}
