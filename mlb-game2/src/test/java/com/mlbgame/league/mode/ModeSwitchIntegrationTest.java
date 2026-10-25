package com.mlbgame.league.mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mlbgame.auth.AuthUser;
import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import com.mlbgame.league.LeagueMode;
import com.mlbgame.player.Education;
import com.mlbgame.player.Hand;
import com.mlbgame.player.Nationality;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerRepository;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.Position;
import com.mlbgame.player.SchoolRegion;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** K2: 상단 MLB | KBO 탭, 모드별 대시보드·슬롯·랭킹, 선수 화면의 모드 맞춤 (docs/kbo-mode.md 1장) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ModeSwitchIntegrationTest {

    static final Cookie KBO = new Cookie(ModeCookie.NAME, "KBO");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    PlayerService playerService;

    private User owner(String loginId) {
        return userRepository.save(new User(loginId, "{noop}pw", loginId));
    }

    private Player mlbPlayer(User owner, String name) {
        return playerRepository.save(Player.create(owner.getId(), name, 7, Position.SS, Hand.R, Hand.R,
                Nationality.USA, Education.COLLEGE, SchoolRegion.US_EAST, 40, Map.of(), List.of()));
    }

    @Test
    void 쿠키_값은_대소문자_무관_모르면_MLB() {
        assertThat(ModeCookie.parse("kbo")).isEqualTo(LeagueMode.KBO);
        assertThat(ModeCookie.parse("MLB")).isEqualTo(LeagueMode.MLB);
        assertThat(ModeCookie.parse("npb")).isEqualTo(LeagueMode.MLB);
        assertThat(ModeCookie.parse(null)).isEqualTo(LeagueMode.MLB);
    }

    @Test
    void 탭을_누르면_쿠키에_기억하고_고정된_곳으로_돌아간다() throws Exception {
        mockMvc.perform(get("/mode/kbo"))
                .andExpect(redirectedUrl("/rankings"))
                .andExpect(header().string("Set-Cookie", containsString("league_mode=KBO")));

        mockMvc.perform(get("/mode/mlb").with(user(new AuthUser(owner("modetab")))))
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(header().string("Set-Cookie", containsString("league_mode=MLB")));
    }

    @Test
    void 대시보드는_모드별_선수만_보인다() throws Exception {
        User owner = owner("modedash");
        mlbPlayer(owner, "메이저선수");
        AuthUser auth = new AuthUser(owner);

        mockMvc.perform(get("/dashboard").with(user(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("메이저선수")))
                .andExpect(content().string(containsString("⚾ MLB 선수 키우기")));

        mockMvc.perform(get("/dashboard").with(user(auth)).cookie(KBO))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("메이저선수"))))
                .andExpect(content().string(not(containsString("준비 중"))))
                .andExpect(content().string(containsString("mode-kbo")))
                .andExpect(content().string(containsString("href=\"/players/new\"")));
    }

    @Test
    void KBO_탭에서는_KBO_선수를_만들_수_있다() throws Exception {
        mockMvc.perform(get("/players/new").with(user(new AuthUser(owner("modenew")))).cookie(KBO))
                .andExpect(status().isOk());
    }

    @Test
    void 슬롯은_모드별로_센다() {
        User owner = owner("modeslot");
        for (int i = 0; i < 3; i++) {
            mlbPlayer(owner, "슬롯선수" + i);
        }
        assertThat(playerService.hasFreeSlot(owner.getId(), LeagueMode.MLB)).isFalse();
        assertThat(playerService.hasFreeSlot(owner.getId(), LeagueMode.KBO)).isTrue();
        assertThat(playerService.listActive(owner.getId(), LeagueMode.KBO)).isEmpty();
    }

    @Test
    void 선수_화면은_선수의_모드를_따르고_쿠키도_맞춘다() throws Exception {
        User owner = owner("modesync");
        Player player = mlbPlayer(owner, "동기화선수");

        mockMvc.perform(get("/players/" + player.getId()).with(user(new AuthUser(owner))).cookie(KBO))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("league_mode=MLB")))
                .andExpect(content().string(containsString("⚾ MLB 선수 키우기")));
    }

    @Test
    void 랭킹은_탭의_모드로_본다() throws Exception {
        mockMvc.perform(get("/rankings").cookie(KBO))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("KBO 랭킹")));
        mockMvc.perform(get("/rankings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MLB 랭킹")));
    }
}
