package com.mlbgame.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private MockHttpServletRequestBuilder signupRequest(String loginId, String password, String confirm, String nickname) {
        return post("/signup").with(csrf())
                .param("loginId", loginId)
                .param("password", password)
                .param("passwordConfirm", confirm)
                .param("nickname", nickname);
    }

    @Test
    void 회원가입_성공시_로그인페이지로_이동하고_비밀번호는_해시로_저장된다() throws Exception {
        mockMvc.perform(signupRequest("Slugger_01", "password123", "password123", "홈런왕"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User saved = userRepository.findByLoginId("slugger_01").orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("홈런왕");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void 아이디는_대소문자_구분없이_중복을_막는다() throws Exception {
        userRepository.save(new User("slugger", passwordEncoder.encode("password123"), "선배"));

        mockMvc.perform(signupRequest("SLUGGER", "password123", "password123", "후배"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("form", "loginId"));
    }

    @Test
    void 닉네임_중복을_막는다() throws Exception {
        userRepository.save(new User("first", passwordEncoder.encode("password123"), "홈런왕"));

        mockMvc.perform(signupRequest("second", "password123", "password123", "홈런왕"))
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("form", "nickname"));
    }

    @Test
    void 입력값_검증에_실패하면_가입되지_않는다() throws Exception {
        mockMvc.perform(signupRequest("ab", "short", "different", "a"))
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("form", "loginId", "password", "passwordMatching", "nickname"))
                .andExpect(content().string(Matchers.containsString("비밀번호가 일치하지 않습니다.")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void CSRF_토큰_없는_가입요청은_거부된다() throws Exception {
        mockMvc.perform(post("/signup")
                        .param("loginId", "nocsrf").param("password", "password123")
                        .param("passwordConfirm", "password123").param("nickname", "토큰없음"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 가입한_계정으로_로그인하면_대시보드로_이동한다() throws Exception {
        userRepository.save(new User("pitcher", passwordEncoder.encode("password123"), "에이스"));

        mockMvc.perform(formLogin("/login").userParameter("loginId").user("Pitcher").password("password123"))
                .andExpect(authenticated().withUsername("pitcher"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() throws Exception {
        userRepository.save(new User("pitcher", passwordEncoder.encode("password123"), "에이스"));

        mockMvc.perform(formLogin("/login").userParameter("loginId").user("pitcher").password("wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 비로그인_사용자는_대시보드에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 대시보드에_닉네임이_표시된다() throws Exception {
        User saved = userRepository.save(new User("catcher", passwordEncoder.encode("password123"), "안방마님"));

        mockMvc.perform(get("/dashboard").with(user(new AuthUser(saved))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("안방마님")));
    }

    @Test
    void 로그인_회원가입_화면이_렌더링된다() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        mockMvc.perform(get("/signup")).andExpect(status().isOk()).andExpect(view().name("auth/signup"));
    }
}
