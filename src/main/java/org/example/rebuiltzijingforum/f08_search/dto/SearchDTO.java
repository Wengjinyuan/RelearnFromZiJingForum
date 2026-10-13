package org.example.rebuiltzijingforum.f08_search.dto;

/** 搜索框和分页控件的输入；sort 可选 relevance/latest。 */
public record SearchDTO(String keyword, int page, int size, String sort) {}
