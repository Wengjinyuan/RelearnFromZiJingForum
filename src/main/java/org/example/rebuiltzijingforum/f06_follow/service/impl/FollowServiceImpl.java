package org.example.rebuiltzijingforum.f06_follow.service.impl;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f06_follow.mapper.FollowMapper;
import org.example.rebuiltzijingforum.f06_follow.service.FollowService;
import org.example.rebuiltzijingforum.f06_follow.dto.FollowPageDTO;
import org.example.rebuiltzijingforum.f06_follow.entity.FollowEntity;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowStatusVO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowUserVO;
import org.example.rebuiltzijingforum.f06_follow.vo.FollowFeedVO;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** F06 业务层：校验用户和关系，再调用 Mapper；新增关注还通知被关注人。 */
@Service
public class FollowServiceImpl implements FollowService {
    private final FollowMapper mapper;
    private final NotificationService notifications;
    private final Clock clock;

    /** Spring 注入数据库层、通知层和时间源。 */
    public FollowServiceImpl(FollowMapper mapper, NotificationService notifications, Clock clock) {
        this.mapper = mapper; this.notifications = notifications; this.clock = clock;
    }

    /** 关注：不能关注自己 → 检查目标 → INSERT 关系 → 通知目标；重复请求返回已关注。 */
    @Transactional
    @Override
    public FollowStatusVO follow(long viewer, long target) {
        if (viewer == target) throw new BusinessException("FOLLOW_SELF", "不能关注自己", HttpStatus.BAD_REQUEST);
        requireUser(target);
        if (!mapper.follows(viewer, target)) {
            try {
                mapper.add(new FollowEntity(viewer, target, LocalDateTime.now(clock)));
                notifications.emit(target, viewer, "FOLLOW", viewer, "FOLLOW:" + viewer + ":" + target, "有人关注了你");
            } catch (DataIntegrityViolationException duplicate) {
                if (!mapper.follows(viewer, target)) throw duplicate;
            }
        }
        return new FollowStatusVO(true, mapper.followerCount(target));
    }

    /** 取关：DELETE 关系；不存在时也稳定返回未关注，粉丝数来自实时 COUNT。 */
    @Transactional
    @Override
    public FollowStatusVO unfollow(long viewer, long target) {
        requireUser(target);
        mapper.remove(viewer, target);
        return new FollowStatusVO(false, mapper.followerCount(target));
    }

    /** 按关系表查询按钮状态，不靠浏览器缓存猜测。 */
    @Transactional(readOnly = true)
    @Override
    public FollowStatusVO status(long viewer, long target) {
        requireUser(target);
        return new FollowStatusVO(mapper.follows(viewer, target), mapper.followerCount(target));
    }

    /** 公开关注列表：查询总数，再查询一页账号。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FollowUserVO> following(long userId, FollowPageDTO query) {
        requireUser(userId); page(query);
        return new PageResponse<>(mapper.following(userId, query.size(), (query.page()-1)*query.size()), mapper.followingCount(userId), query.page(), query.size());
    }

    /** 公开粉丝列表：使用 followed_id 作为过滤条件。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FollowUserVO> followers(long userId, FollowPageDTO query) {
        requireUser(userId); page(query);
        return new PageResponse<>(mapper.followers(userId, query.size(), (query.page()-1)*query.size()), mapper.followerCount(userId), query.page(), query.size());
    }

    /** 关注流从关系表拉取作者再 JOIN 帖子，取关后自然不再出现。 */
    @Transactional(readOnly = true)
    @Override
    public PageResponse<FollowFeedVO> feed(long viewer, FollowPageDTO query) {
        page(query);
        return new PageResponse<>(mapper.feed(viewer, query.size(), (query.page()-1)*query.size()), mapper.feedCount(viewer), query.page(), query.size());
    }

    /** Service 层校验用户存在；Controller 不承担这个业务规则。 */
    private void requireUser(long id) {
        if (!mapper.activeUser(id)) throw new BusinessException("USER_NOT_FOUND", "用户不存在或已禁用", HttpStatus.NOT_FOUND);
    }

    /** 统一限制分页，避免超大 size 直接拖慢数据库。 */
    private void page(FollowPageDTO query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) throw new BusinessException("PAGE_INVALID", "分页参数错误", HttpStatus.BAD_REQUEST);
    }
}
