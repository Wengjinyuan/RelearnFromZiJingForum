package org.example.rebuiltzijingforum.f08_search.service.impl;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f08_search.mapper.SearchMapper;
import org.example.rebuiltzijingforum.f08_search.service.SearchService;
import org.example.rebuiltzijingforum.f08_search.service.SearchIndexService;
import org.example.rebuiltzijingforum.f08_search.dto.SearchDTO;
import org.example.rebuiltzijingforum.f08_search.vo.PostSearchVO;
import org.example.rebuiltzijingforum.f08_search.vo.UserSearchVO;
import org.example.rebuiltzijingforum.f08_search.utils.SearchHighlighter;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** F08 业务层：校验输入，优先可选 ES，失败回退参数化 MySQL，再安全组装高亮 VO。 */
@Service
public class SearchServiceImpl implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private final SearchMapper mapper;
    private final ObjectProvider<SearchIndexService> index;

    /** Spring 注入搜索数据库层。 */
    public SearchServiceImpl(SearchMapper mapper, ObjectProvider<SearchIndexService> index) { this.mapper = mapper; this.index = index; }

    /** 搜帖子：校验 → ES/MySQL → 对标题和摘要转义后插入固定 em 标签 → VO。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<PostSearchVO> posts(SearchDTO query) {
        check(query);
        PageResponse<PostSearchVO> found = null;
        SearchIndexService es = index.getIfAvailable();
        if (es != null) try { found = es.posts(query); }
        catch (RuntimeException unavailable) { log.warn("ES 帖子搜索不可用，回退 MySQL LIKE", unavailable); }
        if (found == null) {
            String pattern = pattern(query.keyword());
            found = new PageResponse<>(mapper.posts(pattern, query.sort(), query.size(), (query.page()-1)*query.size()),
                    mapper.countPosts(pattern), query.page(), query.size());
        }
        return new PageResponse<>(found.items().stream().map(row -> new PostSearchVO(row.id(), row.title(), row.summary(),
                row.authorId(), row.createdAt(), SearchHighlighter.highlight(row.title(), query.keyword()),
                SearchHighlighter.highlight(row.summary(), query.keyword()))).toList(), found.total(), found.page(), found.size());
    }

    /** 搜用户：相同输入规则；只高亮账号/昵称，密码、邮箱、手机号不进入 VO。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<UserSearchVO> users(SearchDTO query) {
        check(query);
        PageResponse<UserSearchVO> found = null;
        SearchIndexService es = index.getIfAvailable();
        if (es != null) try { found = es.users(query); }
        catch (RuntimeException unavailable) { log.warn("ES 用户搜索不可用，回退 MySQL LIKE", unavailable); }
        if (found == null) {
            String pattern = pattern(query.keyword());
            found = new PageResponse<>(mapper.users(pattern, query.size(), (query.page()-1)*query.size()),
                    mapper.countUsers(pattern), query.page(), query.size());
        }
        return new PageResponse<>(found.items().stream().map(row -> new UserSearchVO(row.id(), row.account(), row.displayName(),
                row.signature(), SearchHighlighter.highlight(row.account(), query.keyword()),
                SearchHighlighter.highlight(row.displayName(), query.keyword()))).toList(), found.total(), found.page(), found.size());
    }

    /** 关键词为空/过长或页码越界时在 SQL 执行前拒绝。 */
    private void check(SearchDTO query) {
        String keyword = query.keyword(); int page = query.page(), size = query.size();
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 100)
            throw new BusinessException("KEYWORD_INVALID", "关键词须为1到100字", HttpStatus.BAD_REQUEST);
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException("PAGE_INVALID", "分页参数错误", HttpStatus.BAD_REQUEST);
        if ((long) (page - 1) * size >= 10_000)
            throw new BusinessException("DEEP_PAGE_INVALID", "暂不支持超过10000条的深分页", HttpStatus.BAD_REQUEST);
        if (query.sort() == null || !(query.sort().equals("relevance") || query.sort().equals("latest")))
            throw new BusinessException("SORT_INVALID", "排序仅支持 relevance/latest", HttpStatus.BAD_REQUEST);
    }

    /** 把 !、%、_ 转义，避免用户输入 % 时误匹配全站；真正值仍通过 JDBC 的 ? 绑定。 */
    private String pattern(String keyword) {
        return "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
