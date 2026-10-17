package com.sms.auth;

import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;

public enum Role {
    ADMIN("/admin/dashboard"),
    TEACHER("/teacher/dashboard"),
    STUDENT("/student/dashboard");

    private final String dashboardUrl;

    Role(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }

    public String dashboardUrl() {
        return dashboardUrl;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    /** Finds the role carried by a logged-in user's authorities. */
    public static Optional<Role> from(Collection<? extends GrantedAuthority> authorities) {
        for (Role role : values()) {
            for (GrantedAuthority a : authorities) {
                if (role.authority().equals(a.getAuthority())) {
                    return Optional.of(role);
                }
            }
        }
        return Optional.empty();
    }
}
