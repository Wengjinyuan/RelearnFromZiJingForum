package org.example.rebuiltzijingforum.f09_home.service;

import org.example.rebuiltzijingforum.f09_home.vo.HomeStatisticsVO;
import org.example.rebuiltzijingforum.f09_home.vo.HotTagVO;

import java.util.List;

/** F09 业务契约；是否加入 Redis 缓存不改变 Controller 的调用方式。 */
public interface HomeService {
    /** 统计当前站点用户、公开帖子、今天与昨天发帖数。 */
    HomeStatisticsVO overview();
    /** 读取按公开帖子使用次数排序的热门标签。 */
    List<HotTagVO> hotTags(int limit);
}
