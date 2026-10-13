package org.example.rebuiltzijingforum.f05_interaction.service.impl;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f05_interaction.mapper.InteractionMapper;
import org.example.rebuiltzijingforum.f05_interaction.mapper.LikeMapper;
import org.example.rebuiltzijingforum.f05_interaction.mapper.FavoriteMapper;
import org.example.rebuiltzijingforum.f05_interaction.entity.LikeEntity;
import org.example.rebuiltzijingforum.f05_interaction.entity.FavoriteEntity;
import org.example.rebuiltzijingforum.f05_interaction.dto.InteractionDTO;
import org.example.rebuiltzijingforum.f05_interaction.vo.InteractionVO;
import org.example.rebuiltzijingforum.f05_interaction.vo.PostInteractionVO;
import org.example.rebuiltzijingforum.f05_interaction.service.InteractionService;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 关系与计数在同一事务更新；Controller 只负责把 HTTP 参数转交本类。 */
@Service
public class InteractionServiceImpl implements InteractionService {
    private final InteractionMapper mapper;
    private final LikeMapper likes;
    private final FavoriteMapper favorites;
    private final NotificationService notifications;

    /** Spring 注入数据库层。 */
    public InteractionServiceImpl(InteractionMapper mapper, LikeMapper likes, FavoriteMapper favorites, NotificationService notifications) {
        this.mapper = mapper; this.likes = likes; this.favorites = favorites; this.notifications = notifications;
    }

    /** PUT 表示“确保已点赞”；先校验目标，再写关系和计数，重复 PUT 不多加一次。 */
    @Transactional
    @Override
    public InteractionVO like(long userId, InteractionDTO target) {
        String type = target.type(); long targetId = target.id();
        validateTarget(type, targetId);
        if (likes.find(userId, type, targetId).isEmpty()) {
            try {
                likes.insert(new LikeEntity(userId, type, targetId));
                mapper.changeLikeCount(type, targetId, 1);
                // F07：首次点赞才产生通知；取消后重新点赞使用不同事件语义由业务后续扩展。
                notifications.emit(mapper.author(type, targetId), userId, "LIKE", targetId,
                        "LIKE:" + type + ":" + targetId + ":" + userId, "有人赞了你的内容");
            } catch (DataIntegrityViolationException concurrentDuplicate) {
                if (likes.find(userId, type, targetId).isEmpty()) throw concurrentDuplicate;
            }
        }
        return new InteractionVO(true);
    }

    /** DELETE 表示“确保未点赞”；只有确实删了一条关系才减计数。 */
    @Transactional
    @Override
    public InteractionVO unlike(long userId, InteractionDTO target) {
        String type = target.type(); long targetId = target.id();
        validateTarget(type, targetId);
        if (likes.delete(userId, type, targetId) == 1) mapper.changeLikeCount(type, targetId, -1);
        return new InteractionVO(false);
    }

    /** 收藏与点赞遵守同样的幂等规则，只允许帖子作为收藏目标。 */
    @Transactional
    @Override
    public InteractionVO favorite(long userId, long postId) {
        requirePost(postId);
        if (favorites.find(userId, postId).isEmpty()) {
            try {
                favorites.insert(new FavoriteEntity(userId, postId));
                mapper.changeFavoriteCount(postId, 1);
                notifications.emit(mapper.author("POST", postId), userId, "FAVORITE", postId,
                        "FAVORITE:" + postId + ":" + userId, "有人收藏了你的帖子");
            } catch (DataIntegrityViolationException concurrentDuplicate) {
                if (favorites.find(userId, postId).isEmpty()) throw concurrentDuplicate;
            }
        }
        return new InteractionVO(true);
    }

    /** 取消收藏不存在的关系也返回 false，数据库计数保持不变。 */
    @Transactional
    @Override
    public InteractionVO unfavorite(long userId, long postId) {
        requirePost(postId);
        if (favorites.delete(userId, postId) == 1) mapper.changeFavoriteCount(postId, -1);
        return new InteractionVO(false);
    }

    /** 每次详情页主动发送浏览请求，Mapper 原子增加 PV。 */
    @Transactional
    @Override
    public void view(long postId) {
        if (mapper.recordView(postId) != 1) throw new BusinessException("POST_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND);
    }

    /** 页面刷新后读取关系表中的当前状态，再结合帖子计数返回，避免按钮状态反跳。 */
    @Transactional(readOnly = true)
    @Override
    public PostInteractionVO postState(long userId, long postId) {
        requirePost(postId);
        InteractionMapper.Counts counts = mapper.counts(postId);
        return new PostInteractionVO(likes.find(userId, "POST", postId).isPresent(), favorites.find(userId, postId).isPresent(), counts.views(), counts.likes(), counts.favorites());
    }

    /** 校验 target_type 白名单和目标是否可见，防止拿任意表名拼 SQL。 */
    private void validateTarget(String type, long id) {
        if (!("POST".equals(type) ? mapper.postExists(id) : "COMMENT".equals(type) && mapper.commentExists(id)))
            throw new BusinessException("TARGET_NOT_FOUND", "点赞目标不存在", HttpStatus.NOT_FOUND);
    }

    /** 收藏和帖子状态查询共用帖子可见性验证。 */
    private void requirePost(long id) {
        if (!mapper.postExists(id)) throw new BusinessException("POST_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND);
    }

}
