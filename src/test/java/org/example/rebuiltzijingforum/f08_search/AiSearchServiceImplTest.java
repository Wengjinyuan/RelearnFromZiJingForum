package org.example.rebuiltzijingforum.f08_search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f08_search.config.AiSearchProperties;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.service.AiChatClient;
import org.example.rebuiltzijingforum.f08_search.service.SearchService;
import org.example.rebuiltzijingforum.f08_search.service.impl.AiSearchServiceImpl;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceImplTest {
    @Mock SearchService search;
    @Mock AiChatClient chat;

    @Test
    void rewritesQuestionSearchesForumAndReturnsCitedAnswer() {
        when(chat.complete(anyString(), eq("最近有什么社团活动？"), eq(120)))
                .thenReturn("{\"keyword\":\"社团活动\",\"category\":\"社团活动\"}");
        when(search.posts(any())).thenReturn(new PageResponse<>(List.of(post()), 1, 1, 6));
        when(chat.complete(anyString(), contains("帖子#9"), eq(700)))
                .thenReturn("可以参加编程社招新。[帖子#9]");

        var result = service().answer("最近有什么社团活动？");

        ArgumentCaptor<SearchDTO> query = ArgumentCaptor.forClass(SearchDTO.class);
        verify(search).posts(query.capture());
        assertThat(query.getValue()).isEqualTo(new SearchDTO("社团活动", 1, 6, "relevance", "社团活动"));
        assertThat(result.answer()).contains("[帖子#9]");
        assertThat(result.sources()).singleElement().satisfies(source -> {
            assertThat(source.id()).isEqualTo(9L);
            assertThat(source.title()).isEqualTo("编程社招新");
        });
    }

    @Test
    void fallsBackToOriginalQuestionWhenRewriteIsInvalid() {
        when(chat.complete(anyString(), eq("食堂新窗口"), eq(120))).thenReturn("not-json");
        when(search.posts(any())).thenReturn(new PageResponse<>(List.of(), 0, 1, 6));

        var result = service().answer("食堂新窗口");

        verify(search).posts(new SearchDTO("食堂新窗口", 1, 6, "relevance", null));
        verify(chat, times(1)).complete(anyString(), anyString(), anyInt());
        assertThat(result.sources()).isEmpty();
        assertThat(result.answer()).contains("没有检索到");
    }

    private AiSearchServiceImpl service() {
        AiSearchProperties properties = new AiSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("deepseek-chat");
        return new AiSearchServiceImpl(search, new ObjectMapper(), chat, properties);
    }

    private PostSearchVO post() {
        return new PostSearchVO(9L, "编程社招新", "本周五在大学生活动中心", 2L, "alice", "社团活动",
                10, 2, 1, 0, false, LocalDateTime.of(2026, 10, 19, 12, 0), null, null);
    }
}
