package org.example.rebuiltzijingforum.f04_comment.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f04_comment.dto.CommentPageDTO;
import org.example.rebuiltzijingforum.f04_comment.dto.CreateCommentDTO;
import org.example.rebuiltzijingforum.f04_comment.vo.CommentVO;

import java.util.List;

/** F04 业务层契约：Controller 只看方法签名，具体校验与事务在 impl。 */
public interface CommentService {
    /** 发评论：禁言/帖子/父评论校验后插入，返回可展示的 VO。 */
    CommentVO create(long postId, CurrentUserDTO user, CreateCommentDTO input);
    /** 查一级评论：分页 DTO 统一传页码、数量和排序。 */
    PageResponse<CommentVO> roots(long postId, CommentPageDTO query);
    /** 查楼中楼：根据父评论找根节点，再读取该楼的回复。 */
    List<CommentVO> replies(long parentId, long beforeId, int size);
    /** 删除：只有作者或管理员能软删除。 */
    void delete(long id, CurrentUserDTO user);
}
