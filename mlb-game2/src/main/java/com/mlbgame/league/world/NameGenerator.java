package com.mlbgame.league.world;

import java.util.List;
import java.util.random.RandomGenerator;

/** NPC 가상 이름 생성 (출신 비율: 미국 62% / 중남미 28% / 일본 4% / 한국 3% / 대만 3%) */
final class NameGenerator {

    private static final List<String> US_FIRST = List.of(
            "Jake", "Tyler", "Ryan", "Cody", "Austin", "Brandon", "Dylan", "Mason", "Logan", "Hunter",
            "Cole", "Chase", "Blake", "Grant", "Trevor", "Garrett", "Bryce", "Drew", "Luke", "Nolan",
            "Owen", "Wyatt", "Caleb", "Evan", "Jared", "Kyle", "Shane", "Travis", "Zach", "Derek",
            "Matt", "Chris", "Nick", "Josh", "Brady", "Colton", "Spencer", "Tanner", "Riley", "Carson");
    private static final List<String> US_LAST = List.of(
            "Miller", "Johnson", "Anderson", "Thompson", "Walker", "Harris", "Mitchell", "Turner", "Parker", "Collins",
            "Bennett", "Hayes", "Foster", "Bryant", "Hughes", "Russell", "Griffin", "Wallace", "Porter", "Hudson",
            "Fletcher", "Barnes", "Holland", "Reed", "Sullivan", "Brooks", "Ward", "Cooper", "Kelly", "Price",
            "Murphy", "Graham", "Stone", "Webb", "Tucker", "Lawson", "Dawson", "Harper", "Riley", "Bishop");
    private static final List<String> LATIN_FIRST = List.of(
            "Carlos", "Luis", "Jose", "Miguel", "Rafael", "Juan", "Pedro", "Javier", "Eduardo", "Manuel",
            "Ramon", "Francisco", "Diego", "Andres", "Yordan", "Julio", "Ronald", "Wander", "Eloy", "Gleyber");
    private static final List<String> LATIN_LAST = List.of(
            "Rodriguez", "Martinez", "Hernandez", "Gonzalez", "Perez", "Ramirez", "Torres", "Diaz", "Cruz", "Morales",
            "Reyes", "Castillo", "Vargas", "Ortiz", "Mendoza", "Santana", "Guerrero", "Soto", "Peralta", "Acuna");
    private static final List<String> JAPAN = List.of(
            "Kenta Tanaka", "Yuki Suzuki", "Shota Sato", "Haruto Ito", "Daiki Yamamoto",
            "Ren Nakamura", "Sota Kobayashi", "Kaito Kato", "Riku Watanabe", "Takumi Mori");
    private static final List<String> KOREA = List.of(
            "Min-jun Kim", "Seo-jun Lee", "Do-yun Park", "Ha-jun Choi", "Ji-ho Jung",
            "Jun-woo Kang", "Hyun-woo Cho", "Woo-jin Yoon", "Ji-hoon Jang", "Sung-min Lim");
    private static final List<String> TAIWAN = List.of(
            "Wei-Lun Chen", "Chih-Hao Lin", "Yu-Cheng Wang", "Po-Yu Huang", "Kuan-Ting Liu",
            "Tsung-Che Wu", "Chun-Hsien Tsai", "Yi-Hsuan Chang", "Ming-Jie Yang", "Hsin-Yu Hsu");

    private NameGenerator() {
    }

    static String next(RandomGenerator random) {
        int roll = random.nextInt(100);
        if (roll < 62) return pick(US_FIRST, random) + " " + pick(US_LAST, random);
        if (roll < 90) return pick(LATIN_FIRST, random) + " " + pick(LATIN_LAST, random);
        if (roll < 94) return pick(JAPAN, random);
        if (roll < 97) return pick(KOREA, random);
        return pick(TAIWAN, random);
    }

    private static String pick(List<String> list, RandomGenerator random) {
        return list.get(random.nextInt(list.size()));
    }
}
