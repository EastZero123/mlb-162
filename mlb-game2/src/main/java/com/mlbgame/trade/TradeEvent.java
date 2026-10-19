package com.mlbgame.trade;

import com.mlbgame.common.BaseTimeEntity;
import com.mlbgame.common.Messages;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 트레이드 관련 사건 1건 */
@Entity
@Table(name = "trade_events")
public class TradeEvent extends BaseTimeEntity {

    public enum Kind {
        RUMOR("트레이드 루머"),
        TRADE("트레이드"),
        REQUEST_DENIED("트레이드 요청 거절");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return Messages.text("TradeEvent.Kind." + name(), label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "after_series", nullable = false)
    private int afterSeries;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "from_team_id", nullable = false)
    private Long fromTeamId;

    @Column(name = "to_team_id")
    private Long toTeamId;

    @Column(length = 255)
    private String note;

    protected TradeEvent() {
    }

    public TradeEvent(Long playerId, int seasonYear, int afterSeries, Kind kind, Long fromTeamId, Long toTeamId,
                      String note) {
        this.playerId = playerId;
        this.seasonYear = seasonYear;
        this.afterSeries = afterSeries;
        this.kind = kind;
        this.fromTeamId = fromTeamId;
        this.toTeamId = toTeamId;
        this.note = note;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSeasonYear() {
        return seasonYear;
    }

    public int getAfterSeries() {
        return afterSeries;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getFromTeamId() {
        return fromTeamId;
    }

    public Long getToTeamId() {
        return toTeamId;
    }

    public String getNote() {
        return note;
    }
}
