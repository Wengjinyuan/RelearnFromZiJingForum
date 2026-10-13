package org.example.rebuiltzijingforum.f08_search;

import org.example.rebuiltzijingforum.f08_search.utils.SearchHighlighter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 高亮只允许固定 em 标签，标题原文里的 HTML 必须变成普通文字。 */
class SearchHighlighterTest {
    @Test
    void escapesSourceBeforeAddingControlledMarkup() {
        assertEquals("&lt;script&gt;<em>Java</em>&lt;/script&gt;",
                SearchHighlighter.highlight("<script>Java</script>", "java"));
        assertEquals("<em>J&amp;A</em>", SearchHighlighter.highlight("J&A", "j&a"));
        assertNull(SearchHighlighter.highlight(null, "java"));
    }
}
