package org.example.rebuiltzijingforum.f08_search.vo;

import java.util.List;

/** AI 回答与检索证据分开返回，避免把模型输出冒充成论坛事实。 */
public record AiSearchResponse(String answer, String rewrittenKeyword, String category,
                               List<AiSearchSourceVO> sources) {}
