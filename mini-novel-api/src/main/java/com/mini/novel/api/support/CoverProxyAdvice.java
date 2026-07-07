package com.mini.novel.api.support;

import com.mini.novel.book.entity.Novel;
import com.mini.novel.common.result.Result;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(annotations = RestController.class)
public class CoverProxyAdvice implements ResponseBodyAdvice<Object> {

    private static final String COVER_PREFIX = "https://www.23qb.net/";
    private static final String COVER_PROXY = "/api/cover/";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result) {
            replaceCover(result.data());
        }
        return body;
    }

    private void replaceCover(Object value) {
        if (value instanceof Novel novel) {
            replaceNovelCover(novel);
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                replaceCover(item);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                replaceCover(item);
            }
        }
    }

    private void replaceNovelCover(Novel novel) {
        String cover = novel.getCoverUrl();
        if (cover != null && cover.startsWith(COVER_PREFIX)) {
            novel.setCoverUrl(COVER_PROXY + novel.getId());
        }
    }
}
