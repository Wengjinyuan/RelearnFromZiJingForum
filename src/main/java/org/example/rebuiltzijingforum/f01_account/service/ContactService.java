package org.example.rebuiltzijingforum.f01_account.service;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f01_account.controller.ContactController.Contact;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ContactService {
    private final JdbcTemplate db;
    public ContactService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    public Contact current(CurrentUserDTO actor) {
        return db.queryForObject("SELECT email,mobile FROM user_account WHERE id=?",
                (rs, i) -> new Contact(rs.getString(1), rs.getString(2)), actor.userId());
    }

    @Transactional
    public Contact update(CurrentUserDTO actor, Contact input) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        String mobile = input.mobile() == null || input.mobile().isBlank() ? null : input.mobile().trim();
        try {
            db.update("UPDATE user_account SET email=?,mobile=?,updated_at=? WHERE id=?", email, mobile,
                    LocalDateTime.now(), actor.userId());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("CONTACT_CONFLICT", "邮箱或手机号已被使用", HttpStatus.CONFLICT);
        }
        return new Contact(email, mobile);
    }
}
