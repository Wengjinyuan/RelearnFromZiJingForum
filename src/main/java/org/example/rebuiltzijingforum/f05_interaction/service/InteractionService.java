package org.example.rebuiltzijingforum.f05_interaction.service;

import org.example.rebuiltzijingforum.f05_interaction.dto.InteractionDTO;
import org.example.rebuiltzijingforum.f05_interaction.vo.InteractionVO;
import org.example.rebuiltzijingforum.f05_interaction.vo.PostInteractionVO;

/** F05 的业务契约；Controller 不依赖具体的 SQL 实现类。 */
public interface InteractionService {
    /** 点赞指定帖子或评论，重复 PUT 保持已点赞状态。 */
    InteractionVO like(long userId, InteractionDTO target);
    /** 取消点赞，只有真实删除关系时才减计数。 */
    InteractionVO unlike(long userId, InteractionDTO target);
    /** 收藏帖子。 */
    InteractionVO favorite(long userId, long postId);
    /** 取消收藏。 */
    InteractionVO unfavorite(long userId, long postId);
    /** 记录一次详情访问，浏览量由服务端增加。 */
    void view(long postId);
    /** 读取当前用户的按钮状态及帖子计数。 */
    PostInteractionVO postState(long userId, long postId);
}
