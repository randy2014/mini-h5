package com.mini.novel.api.model;

public class CaptchaVo {
    private String captchaId;
    private String image;

    public CaptchaVo(String captchaId, String image) {
        this.captchaId = captchaId;
        this.image = image;
    }

    public String getCaptchaId() { return captchaId; }
    public String getImage() { return image; }
}
