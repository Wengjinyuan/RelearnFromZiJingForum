package org.example.rebuiltzijingforum.f08_search.service;

import org.example.rebuiltzijingforum.f08_search.vo.AiSearchResponse;
import org.example.rebuiltzijingforum.f08_search.vo.AiSearchStatusVO;

public interface AiSearchService {
    AiSearchStatusVO status();
    AiSearchResponse answer(String question);
}
