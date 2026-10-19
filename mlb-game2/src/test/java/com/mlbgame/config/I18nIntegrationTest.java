package com.mlbgame.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.mlbgame.player.Hand;
import com.mlbgame.player.Position;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 한국어(기본) / 영어 화면 전환 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class I18nIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 언어_정보가_없으면_한국어() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("나만의 선수를 드래프트부터 명예의 전당까지")))
                .andExpect(content().string(Matchers.containsString("<html lang=\"ko\"")));
    }

    @Test
    void lang_파라미터로_영어로_바꾸고_쿠키에_저장한다() throws Exception {
        mockMvc.perform(get("/login").param("lang", "en"))
                .andExpect(cookie().value("lang", "en"))
                .andExpect(content().string(Matchers.containsString("Build your own player")))
                .andExpect(content().string(Matchers.containsString("<html lang=\"en\"")));
    }

    @Test
    void 쿠키가_있으면_그_언어로_보여준다() throws Exception {
        mockMvc.perform(get("/rankings").cookie(new Cookie("lang", "en")))
                .andExpect(content().string(Matchers.containsString("Rankings")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("랭킹"))));
    }

    @Test
    void 쿠키가_없으면_브라우저_언어를_따른다() throws Exception {
        mockMvc.perform(get("/login").header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(content().string(Matchers.containsString("Build your own player")));
        mockMvc.perform(get("/login").header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8"))
                .andExpect(content().string(Matchers.containsString("나만의 선수를")));
        mockMvc.perform(get("/login").header("Accept-Language", "ja-JP"))   // 한국어가 아니면 영어
                .andExpect(content().string(Matchers.containsString("Build your own player")));
    }

    @Test
    void 입력_검증_메시지도_영어로() throws Exception {
        mockMvc.perform(post("/signup").with(csrf()).cookie(new Cookie("lang", "en"))
                        .param("loginId", "ab")
                        .param("password", "password123")
                        .param("passwordConfirm", "different1")
                        .param("nickname", "tester"))
                .andExpect(view().name("auth/signup"))
                .andExpect(content().string(Matchers.containsString("The passwords do not match.")));
    }

    @Test
    void 영어_패치노트는_별도_템플릿() throws Exception {
        mockMvc.perform(get("/patch-notes").cookie(new Cookie("lang", "en")))
                .andExpect(view().name("patch-notes-en"));
        mockMvc.perform(get("/patch-notes"))
                .andExpect(view().name("patch-notes"));
    }

    @Test
    void 요청_밖에서는_enum_표시명이_한국어() {
        assertThat(Position.SS.getLabel()).isEqualTo("유격수");
        assertThat(Hand.R.getLabel()).isEqualTo("우");
    }
}
