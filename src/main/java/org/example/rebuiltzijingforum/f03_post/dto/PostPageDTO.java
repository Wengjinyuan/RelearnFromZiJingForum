package org.example.rebuiltzijingforum.f03_post.dto;

/** 帖子列表查询参数。页码从 1 开始。 */
public class PostPageDTO {
    private int page = 1;
    private int size = 20;
    private String category;
    private String sort = "latest";
    private Long authorId;
    private Boolean essence;

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Boolean getEssence() { return essence; }
    public void setEssence(Boolean essence) { this.essence = essence; }
}
