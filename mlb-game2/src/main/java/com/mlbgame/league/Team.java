package com.mlbgame.league;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** MLB 구단. 데이터는 Flyway 시드(V2)로만 관리한다. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 5)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "name_ko", nullable = false, length = 50)
    private String nameKo;

    @Column(nullable = false, length = 5)
    private String league;

    @Column(nullable = false, length = 10)
    private String division;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    protected Team() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getLeague() {
        return league;
    }

    public String getDivision() {
        return division;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }
}
