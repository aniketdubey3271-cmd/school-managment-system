package com.sms.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByAccessHash(String accessHash);

    Optional<ApiToken> findByRefreshHash(String refreshHash);

    void deleteByUser(User user);

    void deleteByRefreshExpiresAtBefore(LocalDateTime time);
}
