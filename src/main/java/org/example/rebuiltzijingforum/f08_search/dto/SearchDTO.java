package org.example.rebuiltzijingforum.f08_search.dto;

/** 搜索框、分类和分页控件的输入。 */
public record SearchDTO(String keyword, int page, int size, String sort, String category) {}
