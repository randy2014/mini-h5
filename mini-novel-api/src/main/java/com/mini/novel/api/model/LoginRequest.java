package com.mini.novel.api.model;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String mobile;
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
