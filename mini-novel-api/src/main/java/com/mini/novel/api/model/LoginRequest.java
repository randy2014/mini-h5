package com.mini.novel.api.model;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String mobile;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }
}
