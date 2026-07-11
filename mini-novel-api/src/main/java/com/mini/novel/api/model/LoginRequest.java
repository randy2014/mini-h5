package com.mini.novel.api.model;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String mobile;
    private String invitationCode;
    private Boolean confirmCreateNormal;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getInvitationCode() {
        return invitationCode;
    }

    public void setInvitationCode(String invitationCode) {
        this.invitationCode = invitationCode;
    }

    public Boolean getConfirmCreateNormal() {
        return confirmCreateNormal;
    }

    public void setConfirmCreateNormal(Boolean confirmCreateNormal) {
        this.confirmCreateNormal = confirmCreateNormal;
    }
}
