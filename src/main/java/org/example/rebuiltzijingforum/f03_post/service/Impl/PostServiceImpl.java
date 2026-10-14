package org.example.rebuiltzijingforum.f03_post.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.common.security.PostingPermission;
import org.example.rebuiltzijingforum.common.security.Roles;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.f03_post.dto.CreatePostDTO;
import org.example.rebuiltzijingforum.f03_post.dto.PostPageDTO;
import org.example.rebuiltzijingforum.f03_post.dto.UpdatePostDTO;
import org.example.rebuiltzijingforum.f03_post.entity.PostEntity;
import org.example.rebuiltzijingforum.f03_post.entity.PostTagEntity;
import org.example.rebuiltzijingforum.f03_post.entity.TagEntity;
import org.example.rebuiltzijingforum.f03_post.mapper.PostMapper;
import org.example.rebuiltzijingforum.f03_post.mapper.PostTagMapper;
import org.example.rebuiltzijingforum.f03_post.mapper.TagMapper;
import org.example.rebuiltzijingforum.f03_post.service.PostService;
import org.example.rebuiltzijingforum.f03_post.utils.HtmlUtils;
import org.example.rebuiltzijingforum.f03_post.vo.PostDetailVO;
import org.example.rebuiltzijingforum.f03_post.vo.PostSummaryVO;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F03 的 MySQL 强一致版本。
 *
 * <p>这一个 Service 负责把“帖子业务步骤”翻译成数据库操作：</p>
 * <ol>
 *     <li>postMapper 操作 post 帖子主表；</li>
 *     <li>tagMapper 操作 tag 标签表；</li>
 *     <li>postTagMapper 操作 post_tag 帖子与标签的中间表；</li>
 *     <li>userAccountMapper 批量补充作者账号名。</li>
 * </ol>
 *
 * <p>创建、编辑、删除使用事务，是因为一次业务操作会修改多张表。
 * 例如创建帖子时，如果 post 插入成功而 post_tag 插入失败，事务会把 post 的插入也回滚，
 * 避免数据库里出现“帖子存在，但是标签关系只写了一半”的状态。</p>
 */
@Service
public class PostServiceImpl implements PostService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String DELETED = "DELETED";
    private static final Set<String> CATEGORIES = Set.of(
            "校园资讯", "学习交流", "社团活动", "二手交易", "生活服务", "校园树洞", "公告");
    private final PostMapper postMapper;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final UserAccountMapper userAccountMapper;
    private final Clock clock;
    private final PostingPermission postingPermission;
    private final ObjectProvider<SearchSyncService> searchSync;

    public PostServiceImpl(
            PostMapper postMapper,
            TagMapper tagMapper,
            PostTagMapper postTagMapper,
            UserAccountMapper userAccountMapper,
            Clock clock,
            PostingPermission postingPermission,
            ObjectProvider<SearchSyncService> searchSync) {
        this.postMapper = postMapper;
        this.tagMapper = tagMapper;
        this.postTagMapper = postTagMapper;
        this.userAccountMapper = userAccountMapper;
        this.clock = clock;
        this.postingPermission = postingPermission;
        this.searchSync = searchSync;
    }

    @Override
    @Transactional
    public PostDetailVO createPost(CurrentUserDTO currentUser, CreatePostDTO dto) {
        // F11 禁言校验在 common/security/PostingPermission.requireCanPost；过期禁言会自动恢复。
        postingPermission.requireCanPost(currentUser.userId());
        // 第 1 步：校验分类是否合法；“公告”还需要额外检查管理员身份。
        validateCategory(dto.category(), currentUser);
        // 第 2 步：清洗危险 HTML，并保证清洗后的正文仍有可见文字。
        String safeContent = sanitizeAndValidateContent(dto.content());
        LocalDateTime now = LocalDateTime.now(clock);

        // 第 3 步：DTO -> Entity。客户端没有权力提交 authorId、计数、置顶状态等字段，
        // 这些字段全部由服务端根据当前用户或默认业务规则填写。
        PostEntity post = new PostEntity();
        post.setAuthorId(currentUser.userId());
        post.setCategory(dto.category().trim());
        post.setTitle(dto.title().trim());
        post.setContent(safeContent);
        post.setSummary(HtmlUtils.summary(safeContent));
        post.setCoverUrl(HtmlUtils.firstImage(safeContent));
        post.setStatus(PUBLISHED);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);
        post.setIsTop(false);
        post.setIsEssence(false);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        /*
         * postMapper.insert(post) 大致生成：
         * INSERT INTO post(author_id, category, title, content, ...)
         * VALUES (?, ?, ?, ?, ...)
         *
         * INSERT INTO：决定数据写入 post 表。
         * 括号中的列：决定要给哪些字段赋值。
         * VALUES：提供这些列对应的具体值。
         * 返回值：受影响行数；正常插入一篇帖子必须等于 1。
         * 插入成功后，自增主键会回填进 post.id，后面才能拿它建立 post_tag 关系。
         */
        if (postMapper.insert(post) != 1) {
            throw internal("POST_CREATE_FAILED", "帖子创建失败");
        }

        // 第 4 步：使用刚生成的 post.id 写入/更新 tag 和 post_tag。
        // 该方法和上面的 INSERT 处于同一个事务，任意一步失败都会整体回滚。
        replaceTags(post.getId(), dto.tags());

        // 第 5 步：重新取得标签名并组装详情 VO；不把数据库 Entity 直接暴露给前端。
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.postChanged(post.getId()); // 事务提交后才回查 MySQL 更新 ES。
        return toDetail(post, currentUser.account(), loadTagNames(List.of(post.getId())).get(post.getId()));
    }

    @Override
    @Transactional
    public PostDetailVO updatePost(long postId, CurrentUserDTO currentUser, UpdatePostDTO dto) {
        // 编辑也是发布内容：先检查 F11 禁言，再检查这篇帖子的作者权限。
        postingPermission.requireCanPost(currentUser.userId());
        // 先查“存在且可见”的帖子。查不到时直接返回 404，不继续修改。
        PostEntity post = requireVisiblePost(postId);
        // 即使 Controller 已经鉴权，Service 仍必须检查“这个用户能不能改这篇帖子”。
        ensureOwnerOrAdmin(post, currentUser);
        validateCategory(dto.category(), currentUser);
        String safeContent = sanitizeAndValidateContent(dto.content());

        // 只覆盖允许编辑的白名单字段；作者、计数、置顶/精华状态都不能由这个 DTO 修改。
        post.setCategory(dto.category().trim());
        post.setTitle(dto.title().trim());
        post.setContent(safeContent);
        post.setSummary(HtmlUtils.summary(safeContent));
        post.setCoverUrl(HtmlUtils.firstImage(safeContent));
        post.setUpdatedAt(LocalDateTime.now(clock));
        /*
         * updateById(post) 大致生成：
         * UPDATE post
         * SET category=?, title=?, content=?, summary=?, cover_url=?, updated_at=?, ...
         * WHERE id=?
         *
         * UPDATE post：指定修改 post 表。
         * SET：列出要写回的新值。
         * WHERE id=?：只修改当前这一篇帖子；没有 WHERE 会误改整张表。
         * 返回值等于 1，说明确实更新了一行。
         */
        if (postMapper.updateById(post) != 1) {
            throw internal("POST_UPDATE_FAILED", "帖子更新失败");
        }

        // 标签不是 post 表中的一个字符串字段，而是 tag + post_tag 两张表，因此要单独同步。
        replaceTags(postId, dto.tags());

        // 查询作者名和标签名，最后把多张表的数据组合成一个 PostDetailVO。
        String author = accountNames(List.of(post.getAuthorId())).get(post.getAuthorId());
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.postChanged(postId);
        return toDetail(post, author, loadTagNames(List.of(postId)).get(postId));
    }

    /**
     * @获取一页帖子
     * @param query
     * @return
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostSummaryVO> getPosts(PostPageDTO query) {
        // 先限制 page/size，既防止负数，又保证下面拼接的 LIMIT/OFFSET 是受控整数。
        validatePage(query);

        /*
         * 第一次 SQL 只计算符合条件的总记录数，大致为：
         * SELECT COUNT(*) FROM post
         * WHERE status='PUBLISHED' AND deleted_at IS NULL [AND category=?]
         *
         * COUNT(*)：不是取帖子内容，而是告诉前端“一共有多少条”，用于计算总页数。
         * WHERE：保证统计范围和下面真正查询列表的范围完全相同。
         */
        long total = postMapper.selectCount(applyProfileFilters(visiblePosts(query.getCategory()), query));

        // page 从 1 开始。第 1 页跳过 0 条，第 2 页跳过 size 条。
        // 例如 page=3、size=20：offset=(3-1)*20=40，即先跳过前 40 条。
        int offset = (query.getPage() - 1) * query.getSize();

        // wrapper 先只装 WHERE 条件；排序和分页继续追加到同一个 SQL 条件对象上。
        LambdaQueryWrapper<PostEntity> wrapper = applyProfileFilters(visiblePosts(query.getCategory()), query);
        applySort(wrapper, query.getSort());

        /*
         * last(...) 会把字符串直接放到 SQL 最后，最终类似：
         * ... ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 40
         *
         * LIMIT 20：本页最多取 20 行。
         * OFFSET 40：先跳过前 40 行。
         * 这里可以拼接，是因为 size/page 已被 validatePage 限制为整数范围；
         * 不应把用户提交的任意字符串直接传给 last，否则会有 SQL 注入风险。
         */
        wrapper.last("LIMIT " + query.getSize() + " OFFSET " + offset);

        // 第二次 SQL 才真正读取当前页的帖子数据。
        List<PostEntity> posts = postMapper.selectList(wrapper);

        // toSummaries 会批量补充作者和标签，再返回“当前页数据 + 总数 + 页码”。
        return new PageResponse<>(toSummaries(posts), total, query.getPage(), query.getSize());
    }

    private LambdaQueryWrapper<PostEntity> applyProfileFilters(LambdaQueryWrapper<PostEntity> wrapper, PostPageDTO query) {
        if (query.getAuthorId() != null) wrapper.eq(PostEntity::getAuthorId, query.getAuthorId());
        if (Boolean.TRUE.equals(query.getEssence())) wrapper.eq(PostEntity::getIsEssence, true);
        return wrapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSummaryVO> getTopPosts() {
        /*
         * 目标：取得最多 10 篇仍然可见的置顶帖子。
         * 最终 SQL 大致为：
         * SELECT * FROM post
         * WHERE status='PUBLISHED'
         *   AND deleted_at IS NULL
         *   AND is_top=TRUE
         * ORDER BY updated_at DESC, id DESC
         * LIMIT 10
         *
         * visiblePosts(null)：贡献前两个公共可见性条件；null 表示不限制分类。
         * eq(isTop, true)：只留下置顶帖子。
         * orderByDesc(updatedAt)：最近更新的置顶帖排前面。
         * orderByDesc(id)：更新时间相同时用 id 再排序，保证翻页/刷新顺序稳定。
         * LIMIT 10：最多读取十行，避免置顶区无限增长。
         */
        LambdaQueryWrapper<PostEntity> wrapper = visiblePosts(null)
                .eq(PostEntity::getIsTop, true)
                .orderByDesc(PostEntity::getUpdatedAt)
                .orderByDesc(PostEntity::getId)
                .last("LIMIT 10");
        return toSummaries(postMapper.selectList(wrapper));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailVO getPost(long postId) {
        // 一篇详情实际来自三类数据：post 主表、user_account 作者名、tag 标签
        // 3333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333000名。
        PostEntity post = requireVisiblePost(postId);
        String author = accountNames(List.of(post.getAuthorId())).get(post.getAuthorId());
        return toDetail(post, author, loadTagNames(List.of(postId)).get(postId));
    }

    @Override
    @Transactional
    public void deletePost(long postId, CurrentUserDTO currentUser) {
        PostEntity post = requireVisiblePost(postId);
        ensureOwnerOrAdmin(post, currentUser);

        // 帖子删除后不再算作标签的有效使用，因此先查关系表，再把相关标签计数减一。
        decrementTags(currentTagIds(postId));
        LocalDateTime now = LocalDateTime.now(clock);

        /*
         * 这里是“软删除”，不是 DELETE FROM post。最终 SQL 大致为：
         * UPDATE post
         * SET status='DELETED', deleted_at=?, updated_at=?
         * WHERE id=? AND status='PUBLISHED'
         *
         * update(null, wrapper)：第一个参数不传完整 Entity，所有 SET/WHERE 都由 wrapper 描述。
         * eq(id, postId)：只处理指定帖子。
         * eq(status, PUBLISHED)：只允许把当前仍为发布状态的帖子改为删除；
         *                         如果另一个请求已经删过它，这个条件就匹配不到。
         * set(status, DELETED)：让后续可见性查询不再选中它。
         * set(deletedAt, now)：记录实际删除时间，便于审计或恢复。
         * set(updatedAt, now)：说明这篇帖子的最后状态变化发生在当前时刻。
         */
        int updated = postMapper.update(null, new LambdaUpdateWrapper<PostEntity>()
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED)
                .set(PostEntity::getStatus, DELETED)
                .set(PostEntity::getDeletedAt, now)
                .set(PostEntity::getUpdatedAt, now));
        // affected rows 为 0，通常表示帖子在“查询后、更新前”已被其他请求改掉。
        if (updated != 1) {
            throw new BusinessException("POST_STATE_CHANGED", "帖子状态已变化", HttpStatus.CONFLICT);
        }
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.postChanged(postId);
    }

    /**
     * 构造所有公开帖子查询都会使用的 WHERE 条件。
     *
     * <p>基础 SQL：</p>
     * <pre>
     * WHERE status = 'PUBLISHED'
     *   AND deleted_at IS NULL
     *   [AND category = ?]
     * </pre>
     *
     * <p>每一部分的贡献：</p>
     * <ul>
     *     <li>status='PUBLISHED'：排除待审核、驳回、删除等其他业务状态；</li>
     *     <li>deleted_at IS NULL：再排除已经执行软删除的记录；</li>
     *     <li>category=?：仅在用户真的选择了某个分类时追加；</li>
     *     <li>category=all 或不传分类：不追加分类条件，因此可以看到全部分类。</li>
     * </ul>
     */
    private LambdaQueryWrapper<PostEntity> visiblePosts(String category) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                // eq(列, 值) 翻译成“列 = ?”。
                .eq(PostEntity::getStatus, PUBLISHED)
                // isNull(列) 翻译成“列 IS NULL”，不能写成“列 = NULL”。
                .isNull(PostEntity::getDeletedAt);
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            if (!CATEGORIES.contains(category.trim())) {
                throw badRequest("POST_CATEGORY_INVALID", "帖子分类不合法");
            }
            // 只有具体分类才加入 AND category=?；它是在原有可见性条件后继续追加 AND。
            wrapper.eq(PostEntity::getCategory, category.trim());
        }
        return wrapper;
    }

    /**
     * 向已经包含 WHERE 条件的 wrapper 追加 ORDER BY。
     *
     * <p>连续调用 orderByDesc(A).orderByDesc(B) 会得到：</p>
     * <pre>ORDER BY A DESC, B DESC</pre>
     * A 是主要排序字段，只有 A 相同时才比较 B。最后追加 id 是为了让结果顺序稳定。
     */
    private void applySort(LambdaQueryWrapper<PostEntity> wrapper, String sort) {
        String normalized = sort == null ? "latest" : sort.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            // 最新发布：先比较创建时间；同一时刻创建时，id 大的在前。
            case "latest" -> wrapper.orderByDesc(PostEntity::getCreatedAt).orderByDesc(PostEntity::getId);
            // 最近活跃：目前用帖子更新时间代表活跃时间；编辑帖子会改变此顺序。
            case "active" -> wrapper.orderByDesc(PostEntity::getLastReplyAt)
                    .orderByDesc(PostEntity::getCreatedAt).orderByDesc(PostEntity::getId);
            // 热门：依次比较点赞、评论、浏览；前一个字段相同时才比较下一个。
            case "hot" -> wrapper.orderByDesc(PostEntity::getLikeCount)
                    .orderByDesc(PostEntity::getCommentCount)
                    .orderByDesc(PostEntity::getViewCount)
                    .orderByDesc(PostEntity::getId);
            default -> throw badRequest("POST_SORT_INVALID", "排序方式仅支持 latest、active、hot");
        }
    }

    private void validatePage(PostPageDTO query) {
        // page<1 会使 offset 变成负数；size 太大可能一次读取过多数据库记录。
        if (query.getPage() < 1) {
            throw badRequest("POST_PAGE_INVALID", "page 必须大于等于1");
        }
        if (query.getSize() < 1 || query.getSize() > 100) {
            throw badRequest("POST_SIZE_INVALID", "size 必须在1到100之间");
        }
    }

    private void validateCategory(String category, CurrentUserDTO currentUser) {
        // 先 trim，避免“ 学习交流 ”因为前后空格被当成一个新的分类。
        String normalized = category == null ? "" : category.trim();
        if (!CATEGORIES.contains(normalized)) {
            throw badRequest("POST_CATEGORY_INVALID", "帖子分类不合法");
        }
        if ("公告".equals(normalized) && !isAdmin(currentUser)) {
            throw new BusinessException("POST_CATEGORY_FORBIDDEN", "只有管理员可以发布公告", HttpStatus.FORBIDDEN);
        }
    }

    private String sanitizeAndValidateContent(String content) {
        // sanitize 删除 script、onclick、onerror、危险 URL 等不在白名单中的内容。
        String safe = HtmlUtils.sanitize(content);
        // 不能只检查 HTML 字符串非空，例如“<p></p>”有标签但没有真正正文。
        if (HtmlUtils.plainText(safe).isBlank()) {
            throw badRequest("POST_CONTENT_EMPTY", "正文清洗后不能为空");
        }
        return safe;
    }

    /**
     * 用集合差集更新帖子标签关系及 tag.use_count；创建和编辑共用同一套逻辑。
     *
     * <p>例子：帖子原标签 ID 是 {1,2}，用户编辑后想要 {2,3}：</p>
     * <ul>
     *     <li>保留：{2}，数据库什么都不用做；</li>
     *     <li>删除：old - desired = {1}，删除 post_tag 关系并把 tag(1).use_count 减一；</li>
     *     <li>新增：desired - old = {3}，插入 post_tag 关系并把 tag(3).use_count 加一。</li>
     * </ul>
     *
     * <p>不能采用“先删除全部关系，再插入全部关系”的简单写法，因为那会产生不必要的写操作，
     * 也更容易把 use_count 加减错。</p>
     */
    private void replaceTags(long postId, List<String> requestedNames) {
        // 输入标签先去空格、去 #、转小写、去重并限制数量。
        List<String> names = normalizeTags(requestedNames);
        // 查询 post_tag，得到编辑前该帖子关联的标签 ID 集合。
        Set<Long> oldIds = currentTagIds(postId);

        // 每个标签名先在 tag 表中查找；不存在则创建。
        // 最终 Map 的 key 是 tag.id，使用 Map 可以再次保证同一 tagId 不重复。
        Map<Long, TagEntity> desired = names.stream()
                .map(this::findOrCreateTag)
                .collect(Collectors.toMap(TagEntity::getId, tag -> tag, (left, right) -> left, LinkedHashMap::new));
        Set<Long> desiredIds = desired.keySet();

        // removed = oldIds - desiredIds，只包含“以前有、现在不要”的标签。
        Set<Long> removed = new LinkedHashSet<>(oldIds);
        removed.removeAll(desiredIds);
        if (!removed.isEmpty()) {
            /*
             * 最终 SQL 大致为：
             * DELETE FROM post_tag
             * WHERE post_id=? AND tag_id IN (?, ?, ...)
             *
             * DELETE FROM post_tag：只删除关系，不删除 tag 标签本身，也不删除 post。
             * post_id=?：把范围锁定在当前帖子，避免删除其他帖子的同名标签关系。
             * tag_id IN (...)：一次删除所有被移除的标签关系；IN 表示“属于这个集合中的任意一个”。
             */
            postTagMapper.delete(new LambdaQueryWrapper<PostTagEntity>()
                    .eq(PostTagEntity::getPostId, postId)
                    .in(PostTagEntity::getTagId, removed));
            // 关系删掉后，对应标签的有效使用次数也要减一。
            decrementTags(removed);
        }

        // 遍历用户最终想保留的所有标签，只处理“以前没有”的那部分。
        for (Long tagId : desiredIds) {
            if (oldIds.contains(tagId)) {
                // old 和 desired 都有：关系已经存在，跳过可避免触发 post_tag 唯一约束。
                continue;
            }

            /*
             * 新关系大致生成：
             * INSERT INTO post_tag(post_id, tag_id) VALUES (?, ?)
             * post_id 指向帖子，tag_id 指向标签；这一行本身就是二者的关联。
             */
            PostTagEntity relation = new PostTagEntity();
            relation.setPostId(postId);
            relation.setTagId(tagId);
            postTagMapper.insert(relation);
            /*
             * 关系新增后，标签使用次数原子加一：
             * UPDATE tag SET use_count = use_count + 1 WHERE id=?
             *
             * WHERE id=?：只修改刚建立关系的那个标签。
             * use_count = use_count + 1：让数据库基于当前值自增；
             * 不使用“Java 先查旧值、再写旧值+1”，可减少并发请求相互覆盖的问题。
             * setSql 是直接 SQL 片段，只能写开发者控制的固定内容，不能拼接用户输入。
             */
            tagMapper.update(null, new LambdaUpdateWrapper<TagEntity>()
                    .eq(TagEntity::getId, tagId)
                    .setSql("use_count = use_count + 1"));
        }
    }

    /**
     * 按标签名查询 tag；没有时创建并返回。
     *
     * <p>查询 SQL：SELECT * FROM tag WHERE name=? LIMIT 1</p>
     * <ul>
     *     <li>WHERE name=?：只寻找目标标签名；tag.name 还有唯一约束；</li>
     *     <li>LIMIT 1：最多返回一行，表达“这里只需要一个标签对象”。</li>
     * </ul>
     */
    private TagEntity findOrCreateTag(String name) {
        TagEntity existing = tagMapper.selectOne(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getName, name)
                .last("LIMIT 1"));
        if (existing != null) {
            // 已有标签直接复用，不能重复 INSERT，否则会违反 tag(name) 唯一约束。
            return existing;
        }

        // 首次出现该标签：准备 INSERT INTO tag(name, use_count, created_at) VALUES (?, 0, ?)。
        // 此处 use_count 先为 0；建立 post_tag 关系之后再统一加一。
        TagEntity tag = new TagEntity();
        tag.setName(name);
        tag.setUseCount(0);
        tag.setCreatedAt(LocalDateTime.now(clock));
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DataIntegrityViolationException duplicateFromConcurrentRequest) {
            /*
             * 并发场景：请求 A、B 可能同时“没查到 java”，随后都尝试 INSERT。
             * tag(name) 唯一约束只允许一个成功；失败的一方再次 SELECT，复用成功方创建的记录。
             * 唯一约束是最后防线，前面的 SELECT 不能单独解决并发重复问题。
             */
            TagEntity concurrent = tagMapper.selectOne(new LambdaQueryWrapper<TagEntity>()
                    .eq(TagEntity::getName, name)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                return concurrent;
            }
            throw duplicateFromConcurrentRequest;
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        // LinkedHashSet 同时做到：去重 + 保留用户第一次输入时的顺序。
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : tags == null ? List.<String>of() : tags) {
            // “ #Java ” -> “java”，从而让 Java、java、#Java 落到同一个 tag 记录。
            String name = value.trim().replaceFirst("^#+", "").toLowerCase(Locale.ROOT);
            if (!name.isBlank()) {
                if (name.length() > 20) {
                    throw badRequest("POST_TAG_INVALID", "单个标签不能超过20个字符");
                }
                normalized.add(name);
            }
        }
        if (normalized.size() > 10) {
            throw badRequest("POST_TAG_LIMIT", "最多选择10个标签");
        }
        return List.copyOf(normalized);
    }

    private Set<Long> currentTagIds(long postId) {
        /*
         * SQL：SELECT * FROM post_tag WHERE post_id=?
         *
         * WHERE post_id=? 的贡献是：只取当前帖子的关系行。
         * SQL 得到的是 PostTagEntity 列表；stream().map(getTagId) 再只提取每行的 tag_id。
         * 最后收集为 Set，是因为后面要做集合差集，并且集合不允许重复 ID。
         */
        return postTagMapper.selectList(new LambdaQueryWrapper<PostTagEntity>()
                        .eq(PostTagEntity::getPostId, postId))
                .stream().map(PostTagEntity::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void decrementTags(Collection<Long> tagIds) {
        for (Long tagId : tagIds) {
            /*
             * SQL：UPDATE tag
             *      SET use_count = use_count - 1
             *      WHERE id=? AND use_count>0
             *
             * id=?：只减少指定标签。
             * use_count>0：保护计数，避免异常情况下从 0 减成 -1。
             * SET ... - 1：由数据库基于当前值原子递减。
             */
            tagMapper.update(null, new LambdaUpdateWrapper<TagEntity>()
                    .eq(TagEntity::getId, tagId)
                    .gt(TagEntity::getUseCount, 0)
                    .setSql("use_count = use_count - 1"));
        }
    }

    private PostEntity requireVisiblePost(long postId) {
        /*
         * SQL 大致为：
         * SELECT * FROM post
         * WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL
         * LIMIT 1
         *
         * id=?：锁定用户请求的那一篇。
         * status='PUBLISHED'：待审核、驳回等状态不能通过公开详情接口读取。
         * deleted_at IS NULL：软删除后的帖子不可见。
         * LIMIT 1：主键本来最多一条，再写 LIMIT 1 是明确表达只需要单个结果。
         */
        PostEntity post = postMapper.selectOne(new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getId, postId)
                .eq(PostEntity::getStatus, PUBLISHED)
                .isNull(PostEntity::getDeletedAt)
                .last("LIMIT 1"));
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在或已删除", HttpStatus.NOT_FOUND);
        }
        return post;
    }

    private void ensureOwnerOrAdmin(PostEntity post, CurrentUserDTO currentUser) {
        // 两个条件同时成立才拒绝：不是作者，并且也不是管理员。
        // 如果把 && 错写成 ||，作者因为“不是管理员”也会被拒绝。
        if (post.getAuthorId() != currentUser.userId() && !isAdmin(currentUser)) {
            throw new BusinessException("POST_FORBIDDEN", "只能编辑或删除自己的帖子", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isAdmin(CurrentUserDTO currentUser) {
        return Roles.isAdmin(currentUser.role());
    }

    private List<PostSummaryVO> toSummaries(List<PostEntity> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }

        // 先收集这一页所有 post.id，用一批 ID 查询所有关系，避免每篇帖子各查一次标签。
        List<Long> postIds = posts.stream().map(PostEntity::getId).toList();
        Map<Long, List<String>> tags = loadTagNames(postIds);

        // 作者也采用批量查询；distinct 防止同一作者发多篇帖子时重复传入相同 userId。
        Map<Long, String> authors = accountNames(posts.stream().map(PostEntity::getAuthorId).distinct().toList());

        // 这一步不访问数据库，只把 Entity + 作者 Map + 标签 Map 组合成列表 VO。
        return posts.stream().map(post -> new PostSummaryVO(
                post.getId(), post.getAuthorId(), authors.get(post.getAuthorId()), post.getCategory(),
                post.getTitle(), post.getSummary(), post.getCoverUrl(), value(post.getViewCount()),
                value(post.getLikeCount()), value(post.getCommentCount()), value(post.getFavoriteCount()),
                Boolean.TRUE.equals(post.getIsTop()), Boolean.TRUE.equals(post.getIsEssence()),
                tags.getOrDefault(post.getId(), List.of()), post.getCreatedAt(), post.getUpdatedAt())).toList();
    }

    private PostDetailVO toDetail(PostEntity post, String author, List<String> tags) {
        return new PostDetailVO(
                post.getId(), post.getAuthorId(), author, post.getCategory(), post.getTitle(), post.getContent(),
                post.getSummary(), post.getCoverUrl(), post.getStatus(), value(post.getViewCount()),
                value(post.getLikeCount()), value(post.getCommentCount()), value(post.getFavoriteCount()),
                Boolean.TRUE.equals(post.getIsTop()), Boolean.TRUE.equals(post.getIsEssence()),
                tags == null ? List.of() : tags, post.getCreatedAt(), post.getUpdatedAt());
    }

    /**
     * 根据一批“帖子 ID”查出每篇帖子的标签名，避免列表逐帖查询造成 N+1。
     *
     * <p><strong>先辨认查询方向：</strong>这个方法是“帖子 -> 标签”，不是“标签 -> 帖子”。</p>
     *
     * <p>当前方法已知 postIds，例如 [10, 20]，要得到：</p>
     * <pre>
     * 10 -> [java, spring]
     * 20 -> [mysql]
     * </pre>
     *
     * <p>为什么分两次 SQL：</p>
     * <ol>
     *     <li>先从 post_tag 查 post_id 与 tag_id 的对应关系；</li>
     *     <li>再从 tag 表查这些 tag_id 对应的标签名；</li>
     *     <li>最后在 Java 中按照 post_id 分组。</li>
     * </ol>
     *
     * <p>等价逻辑也可以写成 JOIN，但这里分两次查询更容易观察中间表的作用，
     * 也方便使用 MyBatis-Plus 的 BaseMapper。</p>
     *
     * <p><strong>如果需求真的是“输入标签名，反向查帖子”</strong>，方向会变成
     * tag -> post_tag -> post，SQL 应类似：</p>
     * <pre>
     * SELECT p.*
     * FROM tag t
     * JOIN post_tag pt ON pt.tag_id = t.id
     * JOIN post p ON p.id = pt.post_id
     * WHERE t.name = ?
     *   AND p.status = 'PUBLISHED'
     *   AND p.deleted_at IS NULL;
     * </pre>
     * <ul>
     *     <li>FROM tag t：从已知的标签名出发；</li>
     *     <li>pt.tag_id=t.id：找到使用该标签的关系行；</li>
     *     <li>p.id=pt.post_id：由关系行定位帖子；</li>
     *     <li>t.name=?：只选择用户指定的标签；</li>
     *     <li>最后两个条件：只返回仍然公开可见的帖子。</li>
     * </ul>
     */
    private Map<Long, List<String>> loadTagNames(List<Long> postIds) {
        // 先为每个帖子准备空 List。即使某篇帖子没有标签，结果中也会有 postId -> []。
        Map<Long, List<String>> result = new LinkedHashMap<>();
        postIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (postIds.isEmpty()) {
            // 没有帖子 ID 时不要生成“WHERE post_id IN ()”这种非法 SQL。
            return result;
        }

        /*
         * 第一次 SQL：
         * SELECT * FROM post_tag
         * WHERE post_id IN (10, 20, ...)
         * ORDER BY post_id ASC, tag_id ASC
         *
         * post_id IN (...)：一次取得这一页所有帖子的标签关系。
         * IN 的含义是“post_id 等于集合中的任意一个值”，相当于多个 OR：
         * post_id=10 OR post_id=20 OR ...
         * ORDER BY post_id：同一帖子的关系尽量排在一起。
         * ORDER BY tag_id：同一帖子内部的标签顺序稳定。
         * 注意：这一步只得到数字关系，例如 (post=10, tag=3)，还没有标签名。
         */
        List<PostTagEntity> relations = postTagMapper.selectList(new LambdaQueryWrapper<PostTagEntity>()
                .in(PostTagEntity::getPostId, postIds)
                .orderByAsc(PostTagEntity::getPostId)
                .orderByAsc(PostTagEntity::getTagId));
        if (relations.isEmpty()) {
            // 所有帖子都没有标签，不必再查询 tag 表。
            return result;
        }

        /*
         * relations.stream().map(getTagId).distinct().toList()
         * 先从关系行提取 tag_id，再去重，得到例如 [3, 8, 9]。
         *
         * 第二次 SQL（selectByIds）：
         * SELECT * FROM tag WHERE id IN (3, 8, 9)
         *
         * WHERE id IN (...)：只读取第一步真正涉及的标签，而不是扫描/返回整个 tag 表。
         * collect(toMap(id, name))：把查询结果变成 tagId -> tagName，
         * 例如 3->java、8->spring，方便下一步通过数字 ID 找到名称。
         */
        Map<Long, String> names = tagMapper.selectByIds(relations.stream()
                        .map(PostTagEntity::getTagId).distinct().toList())
                .stream().collect(Collectors.toMap(TagEntity::getId, TagEntity::getName));
        // 第三步完全在 Java 内存中完成，不再执行 SQL。
        // 对每条 (post_id, tag_id) 关系：先由 tag_id 找名称，再放入对应 post_id 的列表。
        relations.forEach(relation -> {
            String name = names.get(relation.getTagId());
            if (name != null) {
                result.get(relation.getPostId()).add(name);
            }
        });
        return result;
    }

    private Map<Long, String> accountNames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        /*
         * selectByIds(userIds) 大致生成：
         * SELECT * FROM user_account WHERE id IN (?, ?, ...)
         *
         * 一次查完本页涉及的所有作者，避免“有 20 篇帖子就执行 20 次作者查询”。
         * 随后的 toMap 把 List<UserAccountEntity> 转成 userId -> account，便于组装 VO。
         */
        return userAccountMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(UserAccountEntity::getId, UserAccountEntity::getAccount));
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    private BusinessException internal(String code, String message) {
        return new BusinessException(code, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
