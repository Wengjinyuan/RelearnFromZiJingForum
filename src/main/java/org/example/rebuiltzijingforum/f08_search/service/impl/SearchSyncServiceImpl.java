package org.example.rebuiltzijingforum.f08_search.service.impl;

import org.example.rebuiltzijingforum.f08_search.service.SearchIndexService;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 增量同步：数据库事务提交后才按 ID 回查；默认未开启 ES 时该 Bean 不存在。 */
@Service
@ConditionalOnProperty(name = "forum.search.elasticsearch.enabled", havingValue = "true")
public class SearchSyncServiceImpl implements SearchSyncService {
    private static final Logger log = LoggerFactory.getLogger(SearchSyncServiceImpl.class);
    private final SearchIndexService index;

    /** 注入索引业务接口；索引只负责副本，真正内容由 SearchMapper 回 MySQL 查询。 */
    public SearchSyncServiceImpl(SearchIndexService index) { this.index = index; }

    /** F03/F11 状态变化调用；提交后 syncPost 再查帖子是否仍公开。 */
    @Override
    public void postChanged(long id) { afterCommit(() -> index.syncPost(id), "post", id); }

    /** F01/F02/F11 用户变化调用；封禁后 syncUser 会删除公开索引文档。 */
    @Override
    public void userChanged(long id) { afterCommit(() -> index.syncUser(id), "user", id); }

    /** 在事务外执行时直接同步；事务内仅注册回调，回滚则不会更新 ES。 */
    private void afterCommit(Runnable task, String type, long id) {
        Runnable safe = () -> {
            try { task.run(); }
            catch (RuntimeException unavailable) { log.warn("{} {} 的 ES 增量同步失败，可通过全量重建恢复", type, id, unavailable); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe.run(); }
            });
        } else safe.run();
    }
}
