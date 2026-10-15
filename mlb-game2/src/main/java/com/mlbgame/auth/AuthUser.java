package com.mlbgame.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인 세션에 담기는 사용자 정보. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser} 로 받는다.
 */
public class AuthUser implements UserDetails {

    private final Long id;
    private final String loginId;
    private final String passwordHash;
    private final String nickname;
    private final List<GrantedAuthority> authorities;

    public AuthUser(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.passwordHash = user.getPasswordHash();
        this.nickname = user.getNickname();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
