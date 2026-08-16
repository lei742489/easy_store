package org.jeecgframework.boot.easy_store_boot.app.common;

import org.apache.commons.lang.StringUtils;
import org.jeecgframework.boot.easy_store_boot.app.exception.AppRunTimeException;


public class NullUtil {

    public static void check(String val,String msg){
        if(StringUtils.isEmpty(val))
            throw new AppRunTimeException(msg);
    }

    public static void check(Integer val,String msg){
        if(val == null)
            throw new AppRunTimeException(msg);
    }

    public static void check(Double val,String msg){
        if(val == null)
            throw new AppRunTimeException(msg);
    }

    public static void check(Long val,String msg){
        if(val == null)
            throw new AppRunTimeException(msg);
    }

    public static void check(Object val,String msg){
        if(val == null)
            throw new AppRunTimeException(msg);
    }
}
