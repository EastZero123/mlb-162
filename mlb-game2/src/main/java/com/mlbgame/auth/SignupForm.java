package com.mlbgame.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class SignupForm {

    @NotBlank(message = "{signup.loginId.required}")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "{signup.loginId.pattern}")
    private String loginId;

    @NotBlank(message = "{signup.password.required}")
    @Size(min = 8, max = 64, message = "{signup.password.size}")
    private String password;

    @NotBlank(message = "{signup.passwordConfirm.required}")
    private String passwordConfirm;

    @NotBlank(message = "{signup.nickname.required}")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9_]{2,12}$", message = "{signup.nickname.pattern}")
    private String nickname;

    @AssertTrue(message = "{signup.password.mismatch}")
    public boolean isPasswordMatching() {
        return password == null || passwordConfirm == null || Objects.equals(password, passwordConfirm);
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
