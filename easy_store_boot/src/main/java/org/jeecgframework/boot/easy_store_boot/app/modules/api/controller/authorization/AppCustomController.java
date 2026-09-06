package org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.authorization;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.CommonConstant;
import org.jeecgframework.boot.easy_store_boot.app.common.query.QueryGenerator;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.controller.ApiBaseController;
import org.jeecgframework.boot.easy_store_boot.app.modules.api.vo.Result;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户管理API接口
 */
@RestController
@RequestMapping("api/user/customer")
public class AppCustomController extends ApiBaseController<AppCustomer,IAppCustomerService> {

    @PostMapping("searchKey")
    public Result<?> searchKey(@RequestBody JSONObject param) {
        String key = param.getString("key");
        Set<String> list = new LinkedHashSet<>();
        if(StringUtils.isNotEmpty(key)){

            LambdaQueryWrapper<AppCustomer> wrapper =  new LambdaQueryWrapper<>();
            wrapper.like(AppCustomer::getName, key)
                    .select(AppCustomer::getName)
                    .or().like(AppCustomer::getContactName,key)
                    .or().like(AppCustomer::getMobile,key)
                    .or().like(AppCustomer::getPhone,key)
                    .last("limit " + CommonConstant.AUTO_COMPLETE_MAX_SEARCH_COUNT);

            if(key.matches("^[a-zA-Z]+$"))
                wrapper.or().like(AppCustomer::getPyCode,key);

            List<AppCustomer> dataList= service.list(wrapper);
            if(!dataList.isEmpty()){
                for(AppCustomer customer:dataList){
                    list.add(customer.getName());
                }
            }
        }
        return Result.ok(list);
    }

    @PostMapping("listPage")
    public Result<?> listPage(@RequestBody JSONObject param) {
        AppCustomer entity = JSONObject.toJavaObject(param, getEntityClass());
        Integer current = param.getInteger("current");
        Integer pageSize = param.getInteger("pageSize");
        if (current == null) current = 1;
        if (pageSize == null) pageSize = 15;
        String key = param.getString("key");
        String categoryId = entity.getCategoryId();
        entity.setCategoryId(null);
        QueryWrapper<AppCustomer> queryWrapper = QueryGenerator.initQueryWrapper(entity, param);

        if (categoryId!=null && !categoryId.equals("0")) {
            queryWrapper.eq("category_id", categoryId);

        }
        if (StringUtils.isNotEmpty(key)) {
            queryWrapper.and(wrapper -> wrapper.like("name", key)
                    .or().like("contact_name", key)
                    .or().like("mobile", key)
                    .or().like("phone", key)
                    .or().like("py_code", key));
        }

        Page<AppCustomer> page = new Page<>(current, pageSize);
        IPage<AppCustomer> pageList = service.page(page, queryWrapper);
        return Result.ok(pageList);
    }


    /**
     * 列表
     */
    @PostMapping("list")
    public Result<?> list(@RequestBody JSONObject param) {
        String key = param.getString("key");
        LambdaQueryWrapper<AppCustomer> wrapper = new LambdaQueryWrapper<AppCustomer>()
                .eq(AppCustomer::getStatus, 1)
                .orderByAsc(AppCustomer::getId);
        if(StringUtils.isNotEmpty(key)){
            if(key.matches("^[a-zA-Z]+$")){
                wrapper.like(AppCustomer::getPyCode,key);
            }else{
                wrapper.like(AppCustomer::getName,key);
            }

        }
        return Result.ok(service.list(wrapper));
    }

    @PostMapping("defaultOne")
    public Result<?> defaultOne() {
        AppCustomer customer = service.getOne(new LambdaQueryWrapper<AppCustomer>()
                .eq(AppCustomer::getStatus, 1)
                .orderByAsc(AppCustomer::getId)
                .last("limit 1"));
        return Result.ok(customer);
    }

    @PostMapping("refreshPayable")
    public Result<?> refreshPayable() {
        return Result.ok(service.refreshAllPayable());
    }

}
