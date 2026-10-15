package com.mlbgame.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AuthUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) {
        String normalized = loginId == null ? "" : loginId.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByLoginId(normalized)
                .map(AuthUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 아이디입니다."));
    }
}
