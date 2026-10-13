package org.example.rebuiltzijingforum.f08_search.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;

/** 搜索业务接口；Controller 不知道底层使用 MySQL 或未来的 ES。 */
public interface SearchService {
    /** 搜索公开帖子，返回一页安全摘要。 */
    PageResponse<PostSearchVO> posts(SearchDTO query);
    /** 搜索未封禁用户的公开资料。 */
    PageResponse<UserSearchVO> users(SearchDTO query);
}
