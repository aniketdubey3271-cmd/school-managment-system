package com.sms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in for the mobile app. A short-lived access token is sent with every request; a
 * long-lived refresh token gets a new pair. Refreshing replaces both, so a refresh token
 * works once. Logout or a password change deletes the session.
 */
@Service
@Transactional
public class ApiTokenService {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(30);

    /** Raw tokens, returned to the app once and never stored. */
    public record Issued(String accessToken, String refreshToken, long expiresIn, User user) {
    }

    private final ApiTokenRepository tokens;
    private final UserRepository users;
    private final ProviderManager passwordCheck;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepository tokens, UserRepository users,
                           DatabaseUserDetailsService userDetails, PasswordEncoder passwordEncoder) {
        this.tokens = tokens;
        this.users = users;
        // Same lookup (username or email), BCrypt check and disabled check as the website's login form.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetails);
        provider.setPasswordEncoder(passwordEncoder);
        this.passwordCheck = new ProviderManager(provider);
    }

    public Issued login(String login, String password) {
        Authentication result;
        try {
            result = passwordCheck.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, password));
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return issue(users.findByUsername(result.getName()).orElseThrow());
    }

    /** Starts a new session for an already-verified user. */
    public Issued issue(User user) {
        tokens.deleteByRefreshExpiresAtBefore(LocalDateTime.now());
        ApiToken session = new ApiToken();
        session.setUser(user);
        session.setCreatedAt(LocalDateTime.now());
        return rotate(session);
    }

    public Issued refresh(String refreshToken) {
        ApiToken session = tokens.findByRefreshHash(hash(refreshToken))
                .filter(t -> t.getRefreshExpiresAt().isAfter(LocalDateTime.now()))
                .filter(t -> t.getUser().isEnabled())
                .orElseThrow(() -> new BadCredentialsException("Your session has expired. Sign in again."));
        return rotate(session);
    }

    /** The signed-in user for a valid, unexpired access token whose account is still enabled. */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String accessToken) {
        return tokens.findByAccessHash(hash(accessToken))
                .filter(t -> t.getAccessExpiresAt().isAfter(LocalDateTime.now()))
                .map(ApiToken::getUser)
                .filter(User::isEnabled);
    }

    /** Ends one session; an unknown token is ignored. */
    public void logout(String refreshToken) {
        tokens.findByRefreshHash(hash(refreshToken)).ifPresent(tokens::delete);
    }

    /** Signs the user out of the app on every device. */
    public void revokeAll(User user) {
        tokens.deleteByUser(user);
    }

    private Issued rotate(ApiToken session) {
        LocalDateTime now = LocalDateTime.now();
        String access = newToken();
        String refresh = newToken();
        session.setAccessHash(hash(access));
        session.setAccessExpiresAt(now.plus(ACCESS_TTL));
        session.setRefreshHash(hash(refresh));
        session.setRefreshExpiresAt(now.plus(REFRESH_TTL));
        tokens.save(session);
        return new Issued(access, refresh, ACCESS_TTL.toSeconds(), session.getUser());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
