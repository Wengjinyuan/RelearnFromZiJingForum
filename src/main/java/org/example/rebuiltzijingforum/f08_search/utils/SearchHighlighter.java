package org.example.rebuiltzijingforum.f08_search.utils;

import org.springframework.web.util.HtmlUtils;

/** 只允许服务端插入固定的 em 标签；数据库/ES 原文先 HTML 转义，不能直接拼进 innerHTML。 */
public final class SearchHighlighter {
    private SearchHighlighter() {}

    /** 在原文中按忽略大小写的完整关键词标记，空值仍返回 null。 */
    public static String highlight(String source, String keyword) {
        if (source == null) return null;
        if (keyword == null || keyword.isBlank()) return HtmlUtils.htmlEscape(source);
        String needle = keyword.trim();
        StringBuilder result = new StringBuilder();
        int start = 0;
        for (int i = 0; i <= source.length() - needle.length();) {
            if (source.regionMatches(true, i, needle, 0, needle.length())) {
                result.append(HtmlUtils.htmlEscape(source.substring(start, i)));
                result.append("<em>").append(HtmlUtils.htmlEscape(source.substring(i, i + needle.length()))).append("</em>");
                i += needle.length();
                start = i;
            } else i++;
        }
        result.append(HtmlUtils.htmlEscape(source.substring(start)));
        return result.toString();
    }
}
