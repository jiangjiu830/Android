package com.example.android.notepad.util;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Locale;

/**
 * 搜索结果展示工具：高亮关键字、截取包含关键字的正文片段。
 */
public final class SearchText {

    private SearchText() {
    }

    /** 把文本中所有匹配到的关键字加粗并染色 */
    public static CharSequence highlight(String text, String keyword, int color) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(keyword)) {
            return text;
        }
        int index = indexOfIgnoreCase(text, keyword, 0);
        if (index < 0) {
            return text;
        }

        SpannableString spannable = new SpannableString(text);
        while (index >= 0) {
            int end = Math.min(index + keyword.length(), text.length());
            spannable.setSpan(new ForegroundColorSpan(color), index, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), index, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = indexOfIgnoreCase(text, keyword, end);
        }
        return spannable;
    }

    /**
     * 生成正文摘要：命中关键字时把关键字移到摘要开头附近，
     * 这样用户能直接看到匹配的位置。
     */
    public static String snippet(String text, String keyword, int maxLength) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.isEmpty()) {
            return "";
        }

        int index = TextUtils.isEmpty(keyword) ? -1 : indexOfIgnoreCase(flat, keyword, 0);
        int start = index > 16 ? index - 16 : 0;
        int end = Math.min(flat.length(), start + maxLength);
        String piece = flat.substring(start, end);

        StringBuilder builder = new StringBuilder();
        if (start > 0) {
            builder.append('…');
        }
        builder.append(piece);
        if (end < flat.length()) {
            builder.append('…');
        }
        return builder.toString();
    }

    private static int indexOfIgnoreCase(String text, String keyword, int from) {
        if (from >= text.length()) {
            return -1;
        }
        return text.toLowerCase(Locale.getDefault())
                .indexOf(keyword.toLowerCase(Locale.getDefault()), from);
    }
}
