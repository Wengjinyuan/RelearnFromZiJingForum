package org.example.rebuiltzijingforum.f03_post.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f03_post.dto.CreatePostDTO;
import org.example.rebuiltzijingforum.f03_post.dto.PostPageDTO;
import org.example.rebuiltzijingforum.f03_post.dto.UpdatePostDTO;
import org.example.rebuiltzijingforum.f03_post.vo.PostDetailVO;
import org.example.rebuiltzijingforum.f03_post.vo.PostSummaryVO;

import java.util.List;

public interface PostService {
    PostDetailVO createPost(CurrentUserDTO currentUser, CreatePostDTO dto);
    PostDetailVO updatePost(long postId, CurrentUserDTO currentUser, UpdatePostDTO dto);
    PageResponse<PostSummaryVO> getPosts(PostPageDTO query);
    List<PostSummaryVO> getTopPosts();
    PostDetailVO getPost(long postId);
    void deletePost(long postId, CurrentUserDTO currentUser);
}
