package com.mlbgame.auth;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signup(SignupForm form) {
        String loginId = form.getLoginId().trim().toLowerCase(Locale.ROOT);
        String nickname = form.getNickname().trim();

        if (userRepository.existsByLoginId(loginId)) {
            throw new DuplicateUserException("loginId", "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new DuplicateUserException("nickname", "이미 사용 중인 닉네임입니다.");
        }

        try {
            return userRepository.saveAndFlush(new User(loginId, passwordEncoder.encode(form.getPassword()), nickname));
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 사전 검사를 통과한 경우 — 유니크 제약이 최종 방어선
            throw new DuplicateUserException("loginId", "이미 사용 중인 아이디 또는 닉네임입니다.");
        }
    }
}
