package org.example.rebuiltzijingforum.f06_follow.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f06_follow.dto.FollowPageDTO;
import org.example.rebuiltzijingforum.f06_follow.vo.*;

/** F06 业务接口：Controller 依赖契约，具体事务在 FollowServiceImpl。 */
public interface FollowService {
    /** 关注：验证目标后插入关系并通知目标。 */
    FollowStatusVO follow(long viewer, long target);
    /** 取消关注：删除关系；不存在时也返回未关注。 */
    FollowStatusVO unfollow(long viewer, long target);
    /** 查询当前用户对目标的关注按钮状态。 */
    FollowStatusVO status(long viewer, long target);
    /** 查询某人关注了谁。 */
    PageResponse<FollowUserVO> following(long userId, FollowPageDTO query);
    /** 查询某人的粉丝是谁。 */
    PageResponse<FollowUserVO> followers(long userId, FollowPageDTO query);
    /** 查询当前用户关注的人发布的帖子。 */
    PageResponse<FollowFeedVO> feed(long viewer, FollowPageDTO query);
}
