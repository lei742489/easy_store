package org.jeecgframework.boot.easy_store_boot.app.common;

import net.sourceforge.pinyin4j.PinyinHelper;

public class PinyinUtil {
    public static String getFirstLetters(String chinese) {
        StringBuilder result = new StringBuilder();
        for (char ch : chinese.toCharArray()) {
            if (Character.toString(ch).matches("[\\u4E00-\\u9FA5]")) {
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch);
                if (pinyins != null && pinyins.length > 0) {
                    result.append(pinyins[0].charAt(0));
                }
            } else if (Character.isLetterOrDigit(ch)) {
                result.append(ch);
            }
        }
        return result.toString().toLowerCase();
    }


}