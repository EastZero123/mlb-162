package com.mlbgame.ranking;

import com.mlbgame.common.Messages;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랭킹 조회. 은퇴 스냅샷(`ranking_entries`)만 대상, 기간은 전체 / 이번 달(한국 시간 달력 기준 은퇴일).
 */
@Service
public class RankingService {

    public static final int TOP = 50;
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    static final LocalDateTime ALL_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    public enum Period {
        ALL("전체"),
        MONTH("이번 달");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        public String getLabel() {
            return Messages.text("RankingService.Period." + name(), label);
        }
    }

    public record Row(int rank, RankingEntry entry, double value) {
    }

    private final RankingEntryRepository repository;

    public RankingService(RankingEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Row> top(RankingCategory category, Period period) {
        LocalDateTime from = period == Period.MONTH ? monthStart(LocalDate.now(KOREA)) : ALL_TIME;
        PageRequest page = PageRequest.of(0, TOP * 2, category.sort());   // 0 제외 필터를 위해 넉넉히
        List<RankingEntry> entries = switch (category.getQualifier()) {
            case NONE -> repository.findByRetiredAtGreaterThanEqual(from, page);
            case PLATE_APPEARANCES -> repository.findByRetiredAtGreaterThanEqualAndMlbPaGreaterThanEqual(
                    from, RankingCategory.MIN_PLATE_APPEARANCES, page);
            case INNINGS -> repository.findByRetiredAtGreaterThanEqualAndMlbOutsGreaterThanEqual(
                    from, RankingCategory.MIN_OUTS, page);
        };
        List<RankingEntry> filtered = entries.stream()
                .filter(e -> !category.excludesZero() || category.valueOf(e) > 0)
                .limit(TOP)
                .toList();
        return IntStream.range(0, filtered.size())
                .mapToObj(i -> new Row(i + 1, filtered.get(i), category.valueOf(filtered.get(i))))
                .toList();
    }

    static LocalDateTime monthStart(LocalDate today) {
        return today.withDayOfMonth(1).atStartOfDay();
    }
}
