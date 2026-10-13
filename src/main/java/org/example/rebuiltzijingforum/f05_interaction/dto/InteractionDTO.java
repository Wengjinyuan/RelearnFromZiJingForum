package org.example.rebuiltzijingforum.f05_interaction.dto;

/** Controller 从 URL 构造的目标说明：type=POST/COMMENT，id 是对应主键。 */
public record InteractionDTO(String type, long id) {}
