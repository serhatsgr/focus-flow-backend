package com.serhatsgr.focusflow_backend.entities;

import org.springframework.security.core.GrantedAuthority;

/**
 * FocusFlow kullanıcı rolleri.
 * getAuthority() → "ROLE_USER" / "ROLE_ADMIN" döner (Spring Security
 * standardı).
 */
public enum Role implements GrantedAuthority {

    ROLE_USER,
    ROLE_ADMIN;

    @Override
    public String getAuthority() {
        return name();
    }
}