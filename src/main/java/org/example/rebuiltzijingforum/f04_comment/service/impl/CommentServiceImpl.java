package org.example.rebuiltzijingforum.f04_comment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.common.security.PostingPermission;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f04_comment.mapper.CommentMapper;
import org.example.rebuiltzijingforum.f04_comment.dto.CreateCommentDTO;
import org.example.rebuiltzijingforum.f04_comment.dto.CommentPageDTO;
import org.example.rebuiltzijingforum.f04_comment.entity.CommentEntity;
import org.example.rebuiltzijingforum.f04_comment.vo.CommentVO;
import org.example.rebuiltzijingforum.f04_comment.service.CommentService;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashSet;

/** Controller 调用业务层；业务层先验证，再调用 Mapper 写库，最后组装返回值。 */
@Service
public class CommentServiceImpl implements CommentService {
    private final CommentMapper mapper;
    private final Clock clock;
    private final PostingPermission postingPermission;
    private final NotificationService notifications;
    private final ObjectMapper json;

    /** Spring 注入 Mapper 与时间源；测试可用固定 Clock 验证时间。 */
    public CommentServiceImpl(CommentMapper mapper, Clock clock, PostingPermission postingPermission, NotificationService notifications, ObjectMapper json) {
        this.mapper = mapper; this.clock = clock; this.postingPermission = postingPermission; this.notifications = notifications; this.json = json;
    }

    /** 创建一级评论或回复：帖子校验 → 父评论校验 → INSERT comment → UPDATE post 计数。 */
    @Transactional
    @Override
    public CommentVO create(long postId, CurrentUserDTO user, CreateCommentDTO input) {
        // F11 禁言：common/security/PostingPermission.requireCanPost 查询 user_account 与处罚截止时间。
        postingPermission.requireCanPost(user.userId());
        if (!mapper.visiblePost(postId)) {
            throw error("POST_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND);
        }
        String content = Jsoup.clean(input.content() == null ? "" : input.content(), Safelist.none()).trim();
        if (content.isBlank() || content.length() > 2000) throw error("COMMENT_INVALID", "评论须为1到2000字", HttpStatus.BAD_REQUEST);
        List<String> images = input.imageUrls() == null ? List.of() : input.imageUrls();
        if (images.size() > 9) throw error("IMAGE_INVALID", "评论最多9张图片", HttpStatus.BAD_REQUEST);
        for (String url : images) {
            try {
                URI uri = URI.create(url);
                boolean local = uri.getScheme() == null && uri.getHost() == null
                        && url.startsWith("/uploads/posts/") && !url.contains("..");
                boolean remote = ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null;
                if ((!local && !remote) || url.length() > 1024)
                    throw new IllegalArgumentException();
            } catch (RuntimeException invalid) {
                throw error("IMAGE_INVALID", "图片地址必须是本站上传地址或 HTTP(S) URL", HttpStatus.BAD_REQUEST);
            }
        }
        Long rootId = null;
        Long replyUserId = null;
        if (input.parentId() != null) {
            CommentEntity parent = mapper.find(input.parentId()).orElseThrow(() -> error("PARENT_NOT_FOUND", "父评论不存在", HttpStatus.NOT_FOUND));
            if (parent.postId() != postId || parent.deleted()) throw error("PARENT_INVALID", "不能回复其他帖子或已删除评论", HttpStatus.BAD_REQUEST);
            rootId = parent.rootId() == null ? parent.id() : parent.rootId();
            replyUserId = parent.authorId();
        }
        long id = mapper.insert(postId, user.userId(), input.parentId(), rootId, replyUserId, content, writeImages(images), java.time.LocalDateTime.now(clock));
        if (mapper.incrementPostCount(postId) != 1) throw error("POST_CHANGED", "帖子状态已变化", HttpStatus.CONFLICT);
        // F07 通知与评论在同一事务：回复通知父评论作者；一级评论通知帖子作者。
        long recipient = replyUserId == null ? mapper.postAuthor(postId) : replyUserId;
        notifications.emit(recipient, user.userId(), replyUserId == null ? "COMMENT" : "REPLY", id,
                "COMMENT:" + id, replyUserId == null ? "有人评论了你的帖子" : "有人回复了你的评论");
        CommentEntity created = mapper.find(id).orElseThrow();
        return view(created, namesFor(List.of(created)));
    }

    /** 一级评论分页：先查一页根节点，再用一次 SQL 查这些根节点的回复预览。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<CommentVO> roots(long postId, CommentPageDTO query) {
        if (!mapper.visiblePost(postId)) throw error("POST_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND);
        int page = query.page(), size = query.size(); String sort = query.sort();
        if (page < 1 || size < 1 || size > 100) throw error("PAGE_INVALID", "分页参数错误", HttpStatus.BAD_REQUEST);
        if (!List.of("hot", "asc", "desc").contains(sort)) throw error("SORT_INVALID", "排序参数错误", HttpStatus.BAD_REQUEST);
        long count = mapper.countRoots(postId);///本帖根评论总数
        List<CommentEntity> roots = mapper.roots(postId, sort, size, (page - 1) * size);///本帖当前页根评论
        /// 这里不能对每个根评论逐一查回复，否则 20 个根节点就会多发 20 次 SQL。
        List<CommentEntity> replies = mapper.previewReplies(roots.stream().map(CommentEntity::id).toList());
        List<CommentEntity> all = new ArrayList<>(roots);
        all.addAll(replies);
        Map<Long, String> names = namesFor(all); // 再一条 SQL 获取全部作者名，不会逐评论查用户。
        Map<Long, List<CommentVO>> previews = new HashMap<>();
        for (CommentEntity reply : replies)
            previews.computeIfAbsent(reply.rootId(), ignored -> new ArrayList<>()).add(view(reply, names));///对预览
        return new PageResponse<>(roots.stream().map(root -> withReplies(view(root, names), previews.getOrDefault(root.id(), List.of()))).toList(), count, page, size);
        /// 对每一条根评论
    }

    /** 先把任何子评论归到 rootId，再按 id 游标取回复；返回倒序历史。 */
    @Transactional(readOnly = true)
    @Override
    public List<CommentVO> replies(long parentId, long beforeId, int size) {
        CommentEntity parent = mapper.find(parentId).orElseThrow(() -> error("COMMENT_NOT_FOUND", "评论不存在", HttpStatus.NOT_FOUND));
        if (!mapper.visiblePost(parent.postId())) throw error("POST_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND);
        if (size < 1 || size > 100) throw error("SIZE_INVALID", "size 必须在1到100之间", HttpStatus.BAD_REQUEST);
        long rootId = parent.rootId() == null ? parent.id() : parent.rootId();///没有跟那么你的父节点就是根
        List<CommentEntity> rows = mapper.replies(rootId, size, beforeId);
        Map<Long, String> names = namesFor(rows);
        return rows.stream().map(row -> view(row, names)).toList();
    }

    /** 删除前验证作者/管理员；Mapper 保留占位，帖子计数随事务减一。 */
    @Transactional
    @Override
    public void delete(long id, CurrentUserDTO user) {
        CommentEntity row = mapper.find(id).orElseThrow(() -> error("COMMENT_NOT_FOUND", "评论不存在", HttpStatus.NOT_FOUND));
        if (row.authorId() != user.userId() && !"ADMIN".equals(user.role())) throw error("COMMENT_FORBIDDEN", "只能删除自己的评论", HttpStatus.FORBIDDEN);
        if (mapper.markDeleted(id, java.time.LocalDateTime.now(clock)) == 1) mapper.decrementPostCount(row.postId());
    }

    /** 把已删除评论改成占位文字，前端无需看到已删除的原文。 */
    private CommentVO view(CommentEntity row, Map<Long, String> names) {
        return new CommentVO(row.id(), row.postId(), row.authorId(), names.get(row.authorId()), row.parentId(), row.rootId(), row.replyUserId(),
                row.replyUserId() == null ? null : names.get(row.replyUserId()),
                row.deleted() ? "该评论已删除" : row.content(), row.likeCount(), row.deleted(), row.createdAt(),
                row.deleted() ? List.of() : readImages(row.imageUrls()), List.of());
    }

    /** 从本页 Entity 收集去重的用户 ID，交 Mapper 一次 IN 查询，避免作者信息 N+1。 */
    private Map<Long, String> namesFor(Collection<CommentEntity> rows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CommentEntity row : rows) {
            ids.add(row.authorId());
            if (row.replyUserId() != null) ids.add(row.replyUserId());
        }
        return mapper.accountNames(ids);
    }

    /** VO 是只读 record；复制根评论并装入预览回复，不修改数据库实体。 */
    private CommentVO withReplies(CommentVO root, List<CommentVO> replies) {
        return new CommentVO(root.id(), root.postId(), root.authorId(), root.authorName(), root.parentId(), root.rootId(),
                root.replyUserId(), root.replyUserName(), root.content(), root.likeCount(), root.deleted(), root.createdAt(), root.imageUrls(), replies);
    }

    private String writeImages(List<String> images) {
        try { return json.writeValueAsString(images); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("图片地址序列化失败", invalid); }
    }

    /** 从数据库 JSON 还原图片 URL 列表；旧数据为空时给前端空数组而不是 null。 */
    private List<String> readImages(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("数据库图片地址格式错误", invalid); }
    }

    /** 统一创建带 HTTP 状态的业务异常，交给 GlobalExceptionHandler 转成 JSON。 */
    private BusinessException error(String code, String message, HttpStatus status) { return new BusinessException(code, message, status); }

}
