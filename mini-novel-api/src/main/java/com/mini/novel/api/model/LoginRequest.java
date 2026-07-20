package com.mini.novel.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    @NotBlank
    private String mobile;
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
    @NotBlank
    private String captchaId;
    @NotBlank
    private String captchaCode;
    private String invitationCode;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }

    public String getInvitationCode() {
        return invitationCode;
    }

    public void setInvitationCode(String invitationCode) {
        this.invitationCode = invitationCode;
    }
}
