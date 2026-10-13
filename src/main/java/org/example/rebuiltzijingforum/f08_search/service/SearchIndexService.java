package org.example.rebuiltzijingforum.f08_search.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;

/** ES 可重建副本的操作契约；只在 forum.search.elasticsearch.enabled=true 时有实现 Bean。 */
public interface SearchIndexService {
    void syncPost(long id);
    void syncUser(long id);
    void rebuild();
    PageResponse<PostSearchVO> posts(SearchDTO query);
    PageResponse<UserSearchVO> users(SearchDTO query);
}
