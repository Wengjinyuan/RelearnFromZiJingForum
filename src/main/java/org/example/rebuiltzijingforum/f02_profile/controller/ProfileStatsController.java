package org.example.rebuiltzijingforum.f02_profile.controller;

import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class ProfileStatsController {
    private final JdbcTemplate db;
    public ProfileStatsController(JdbcTemplate db) { this.db = db; }

    public record Stats(long followingCount, long followerCount, long postCount, long essenceCount, long likeCount) {}

    @GetMapping("/{userId}/stats")
    public ApiResponse<Stats> stats(@PathVariable long userId) {
        long following = db.queryForObject("SELECT COUNT(*) FROM follow_relation WHERE follower_id=?", Long.class, userId);
        long followers = db.queryForObject("SELECT COUNT(*) FROM follow_relation WHERE followed_id=?", Long.class, userId);
        long posts = db.queryForObject("SELECT COUNT(*) FROM post WHERE author_id=? AND status='PUBLISHED'", Long.class, userId);
        long essence = db.queryForObject("SELECT COUNT(*) FROM post WHERE author_id=? AND status='PUBLISHED' AND is_essence=TRUE", Long.class, userId);
        long likes = db.queryForObject("SELECT COALESCE(SUM(like_count),0) FROM post WHERE author_id=? AND status='PUBLISHED'", Long.class, userId);
        return ApiResponse.success(new Stats(following, followers, posts, essence, likes));
    }
}
