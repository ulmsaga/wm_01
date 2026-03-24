package com.mobigen.aiop.nttpoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Accept-Language 헤더 기반 Locale 해석.
 * 프론트엔드가 모든 요청에 Accept-Language: ko|en|ja 를 전송하면
 * LocaleContextHolder 가 해당 Locale 로 설정되어
 * GlobalExceptionHandler 의 MessageSource 메시지가 해당 언어로 반환된다.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(
                Locale.KOREAN,          // ko
                Locale.ENGLISH,         // en
                Locale.JAPANESE         // ja
        ));
        resolver.setDefaultLocale(Locale.KOREAN); // 헤더 없거나 미지원 언어 → 한국어
        return resolver;
    }
}
