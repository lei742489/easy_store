package org.jeecgframework.boot.easy_store_boot.app.common;

import java.math.BigDecimal;

public class AmountToChineseUtil {

    private static final String[] CN_UPPER_NUMBER = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UNIT = {"", "拾", "佰", "仟"};
    private static final String[] CN_SECTION = {"", "万", "亿", "兆"};
    private static final String CN_DOLLAR = "元";
    private static final String CN_INTEGER = "整";
    private static final String[] CN_DECIMAL_UNIT = {"角", "分"};

    /**
     * 将金额转为中文大写
     *
     * @param amount 金额（单位：元）
     * @return 中文大写金额，如 壹元肆角伍分
     */
    public static String toChinese(BigDecimal amount) {
        if (amount == null) {
            return "零元整";
        }

        long num = amount.movePointRight(2).setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
        if (num == 0) {
            return "零元整";
        }

        StringBuilder result = new StringBuilder();

        // 小数部分
        int scale = (int) (num % 100);
        // 整数部分
        int integerPart = (int) (num / 100);

        // 处理整数部分
        if (integerPart > 0) {
            result.append(integerToChinese(integerPart));
            result.append(CN_DOLLAR);
        }

        // 处理小数部分
        if (scale > 0) {
            int jiao = scale / 10;
            int fen = scale % 10;
            if (jiao > 0) {
                result.append(CN_UPPER_NUMBER[jiao]).append(CN_DECIMAL_UNIT[0]);
            }
            if (fen > 0) {
                result.append(CN_UPPER_NUMBER[fen]).append(CN_DECIMAL_UNIT[1]);
            }
        } else {
            result.append(CN_INTEGER);
        }

        return result.toString();
    }

    private static String integerToChinese(int number) {
        StringBuilder result = new StringBuilder();
        int unitPos = 0; // 节权位：万、亿、兆
        boolean zero = true;

        while (number > 0) {
            int section = number % 10000;
            if (section != 0) {
                String sectionChinese = sectionToChinese(section);
                if (!zero) {
                    result.insert(0, CN_UPPER_NUMBER[0]);
                }
                result.insert(0, sectionChinese + CN_SECTION[unitPos]);
            } else {
                zero = false;
            }
            number = number / 10000;
            unitPos++;
        }
        return result.toString();
    }

    private static String sectionToChinese(int section) {
        StringBuilder sectionChinese = new StringBuilder();
        int unitPos = 0;
        boolean zero = true;

        while (section > 0) {
            int digit = section % 10;
            if (digit != 0) {
                sectionChinese.insert(0, CN_UPPER_NUMBER[digit] + CN_UNIT[unitPos]);
                zero = false;
            } else {
                if (!zero) {
                    sectionChinese.insert(0, CN_UPPER_NUMBER[0]);
                }
                zero = true;
            }
            section = section / 10;
            unitPos++;
        }

        return sectionChinese.toString();
    }
}
