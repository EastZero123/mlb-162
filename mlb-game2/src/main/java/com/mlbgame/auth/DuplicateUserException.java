package com.mlbgame.auth;

public class DuplicateUserException extends RuntimeException {

    private final String field;

    public DuplicateUserException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** 중복된 폼 필드명 (loginId / nickname) */
    public String getField() {
        return field;
    }
}
