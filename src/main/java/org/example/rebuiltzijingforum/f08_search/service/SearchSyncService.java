package org.example.rebuiltzijingforum.f08_search.service;

/** 业务表提交后再按 ID 回查 MySQL；不拿旧事件正文覆盖新索引。 */
public interface SearchSyncService {
    void postChanged(long id);
    void userChanged(long id);
}
