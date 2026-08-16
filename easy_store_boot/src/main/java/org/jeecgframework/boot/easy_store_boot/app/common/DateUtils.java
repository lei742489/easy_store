package org.jeecgframework.boot.easy_store_boot.app.common;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {

    public static ThreadLocal<SimpleDateFormat> datetimeFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    public static ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd");
        }
    };

    public static ThreadLocal<SimpleDateFormat> orderFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss");
        }
    };

    public static String now() {
        return datetimeFormat.get().format(getCalendar().getTime());
    }


    public static Calendar getCalendar() {
        return Calendar.getInstance();
    }

    public static String format(Date date) {
        if(date == null) return "";
        return datetimeFormat.get().format(date);
    }

    public static String formatDate(Date date) {
        if(date == null) return "";
        return dateFormat.get().format(date);
    }

    public static String formatOrder(Date date) {
        if(date == null) date= new Date();
        return orderFormat.get().format(date);
    }

}
