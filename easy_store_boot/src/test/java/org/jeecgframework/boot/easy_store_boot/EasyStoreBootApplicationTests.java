package org.jeecgframework.boot.easy_store_boot;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.common.PinyinUtil;
import org.jeecgframework.boot.easy_store_boot.app.common.SqliteTestUtils;

import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppCustomer;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoods;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppGoodsCategory;
import org.jeecgframework.boot.easy_store_boot.app.modules.entity.AppSupplier;
import org.jeecgframework.boot.easy_store_boot.app.modules.mapper.AppGoodsCategoryMapper;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppCustomerService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppGoodsService;
import org.jeecgframework.boot.easy_store_boot.app.modules.service.IAppSupplierService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SpringBootTest
class EasyStoreBootApplicationTests {
    @Autowired
    private IAppSupplierService appSupplierService;
    @Autowired
    private AppGoodsCategoryMapper appGoodsCategoryMapper;
    @Autowired
    private IAppGoodsService appGoodsService;
    @Autowired
    private IAppCustomerService appCustomerService;

    @Test
    void contextLoads() {
        synCustomer();
    }

    /**
     * 同步供应商
     */
    private void synSupplier(){
        SqliteTestUtils sqliteTestUtils = new SqliteTestUtils();
        List<JSONObject> result =  sqliteTestUtils.queryAllFromTable("SELECT * FROM companies where tye = 2 and is_del=0");
        List<AppSupplier> appSuppliers = new ArrayList<>();
        for(JSONObject map : result){
            AppSupplier appSupplier = new AppSupplier();
            appSupplier.setName(map.getString("name"));
            appSupplier.setContactName(map.getString("linkman"));
            appSupplier.setMobile(map.getString("mobile"));
            appSupplier.setPhone(map.getString("tel"));
            appSupplier.setDefPayable(map.getDouble("init_amt"));
            appSupplier.setPayable(map.getDouble("cur_amt"));
            appSuppliers.add(appSupplier);
        }
        appSupplierService.saveBatch(appSuppliers);
    }

    /**
     * 同步客户
     */
    private void synCustomer(){
        SqliteTestUtils sqliteTestUtils = new SqliteTestUtils();
        List<JSONObject> result =  sqliteTestUtils.queryAllFromTable("SELECT * FROM companies where tye = 1 and is_del=0");
        List<AppCustomer> appSuppliers = new ArrayList<>();
        for(JSONObject map : result){
            AppCustomer appCustomer = new AppCustomer();
            appCustomer.setCreateTime(new Date());
            appCustomer.setName(map.getString("name"));
            appCustomer.setContactName(map.getString("linkman"));
            appCustomer.setMobile(map.getString("mobile"));
            appCustomer.setPhone(map.getString("tel"));
            appCustomer.setDefPayable(map.getDouble("init_amt"));
            appCustomer.setPayable(map.getDouble("cur_amt"));
            appSuppliers.add(appCustomer);
        }
        appCustomerService.saveOrUpdateBatch(appSuppliers);

    }

    /**
     * 同步货品分类
     */
    private void synGoodsCategory(){
        SqliteTestUtils sqliteTestUtils = new SqliteTestUtils();
        List<JSONObject> result =  sqliteTestUtils.queryAllFromTable("SELECT * FROM ptypes where is_del=0 ");
        for(JSONObject map : result){
            AppGoodsCategory appGoodsCategory = new AppGoodsCategory();
            Integer pid = map.getInteger("pid");
            String title = map.getString("name");
            Integer id =map.getInteger("id");
            if(StringUtils.isEmpty(title) || id==1)continue;

            if(pid == null || pid == 1) pid = 0;
            appGoodsCategory.setParentId(pid);
            appGoodsCategory.setId(id);
            appGoodsCategory.setTitle(title);
            appGoodsCategory.setRoot(0);
            appGoodsCategory.setPyCode(PinyinUtil.getFirstLetters(title));

            appGoodsCategoryMapper.insertWithId(appGoodsCategory);
        }

    }

    /**
     * 同步货品
     *
     */
    /**
     * 同步货品分类
     */
    private void synGoods(){
        SqliteTestUtils sqliteTestUtils = new SqliteTestUtils();
        List<JSONObject> result =  sqliteTestUtils.queryAllFromTable("SELECT * FROM products where is_del=0");
        List<AppGoods> appGoodsList = new ArrayList<>();
        for(JSONObject map : result){
            AppGoods appGoods = new AppGoods();
            appGoods.setId(map.getInteger("id"));
            appGoods.setTitle(map.getString("name"));
            appGoods.setGoodsCode(map.getString("code"));
            String supplierId = map.getString("company_id");
            appGoods.setSupplierId(supplierId!=null && !supplierId.equals("0") ? supplierId : null);
            appGoods.setCategoryId(map.getString("ptype_id"));
            appGoods.setPurPrc(map.getDouble("pur_prc"));
            appGoods.setSalePrc(map.getDouble("sale_prc"));
            appGoods.setTradePrc(map.getDouble("trade_prc"));
            appGoods.setInitCost(map.getInteger("init_prc"));
            appGoods.setInitStock(map.getDouble("init_stock"));
            appGoods.setStock(map.getDouble("cur_stock"));
            appGoods.setMinStock(map.getDouble("min_stock"));
            appGoods.setMaxStock(map.getDouble("max_stock"));
            appGoods.setNote(map.getString("remark"));
            appGoods.setCreateTime(map.getDate("create_at"));
            appGoods.setUpdateTime(map.getDate("revise_at"));
            appGoods.setUnit(map.getString("unit"));
            appGoodsList.add(appGoods);

        }
        if(!appGoodsList.isEmpty()){
            appGoodsService.saveOrUpdateBatch(appGoodsList);
        }
    }

}
