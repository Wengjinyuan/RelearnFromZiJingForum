package org.example.rebuiltzijingforum.f03_post.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/** 富文本清洗、摘要和首图提取。 */
public final class HtmlUtils {
    private static final int SUMMARY_LIMIT = 20;
    private static final Safelist POST_SAFELIST = Safelist.relaxed()
            .addTags("pre", "code")
            .addAttributes("img", "width", "height")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto");

    private HtmlUtils() {}

    public static String sanitize(String html) {
        if (html == null) {
            return "";
        }
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        Document input = Jsoup.parseBodyFragment(html);
        input.select("img[src]").forEach(image -> {
            String src = image.attr("src");
            if (src.startsWith("/uploads/")) image.attr("src", "https://local.invalid" + src);
        });
        return Jsoup.clean(input.body().html(), "", POST_SAFELIST, settings)
                .replace("src=\"https://local.invalid/uploads/", "src=\"/uploads/").trim();
    }

    public static String plainText(String safeHtml) {
        return Jsoup.parseBodyFragment(safeHtml == null ? "" : safeHtml).text().trim();
    }

    public static String summary(String safeHtml) {
        String text = plainText(safeHtml);
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "…";
    }

    public static String firstImage(String safeHtml) {
        Element image = Jsoup.parseBodyFragment(safeHtml == null ? "" : safeHtml)
                .selectFirst("img[src]");
        return image == null ? null : image.attr("src");
    }
}
