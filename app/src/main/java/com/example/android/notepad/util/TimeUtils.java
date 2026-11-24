package com.example.android.notepad.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 时间格式化工具：笔记列表中的「时间戳」显示逻辑。
 * <p>
 * 今天的笔记显示「今天 09:30」，昨天的显示「昨天 18:05」，
 * 今年内的显示「3月5日」，更早的显示「2025年3月5日」。
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    /** 列表项使用的相对时间戳（今天 / 昨天 / 月日 / 年月日）。 */
    public static String formatListTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);

        if (isSameDay(now, target)) {
            return "今天 " + format(millis, "HH:mm");
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, target)) {
            return "昨天 " + format(millis, "HH:mm");
        }

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
            return format(millis, "M月d日");
        }
        return format(millis, "yyyy年M月d日");
    }

    /** 编辑器底部使用的完整时间。 */
    public static String formatDateTime(long millis) {
        if (millis <= 0) {
            return "—";
        }
        return format(millis, "yyyy-MM-dd HH:mm");
    }

    /** 备份文件名使用的时间戳（yyyyMMdd-HHmm）。 */
    public static String formatFileNameStamp(long millis) {
        return format(millis, "yyyyMMdd-HHmm");
    }

    /** 判断两个 Calendar 是否同一天（只看年 + 年内第几天）。 */
    private static boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    /** 按指定格式格式化；每次新建 SimpleDateFormat，避免线程安全问题。 */
    private static String format(long millis, String pattern) {
        Date date = new Date(millis);
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }
}
