package com.mlbgame.config;

import com.mlbgame.common.Messages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * 한국어 / 영어 서비스.
 * <ul>
 *   <li>아무 주소에 {@code ?lang=en} / {@code ?lang=ko} 를 붙이면 언어가 바뀌고 쿠키(lang)에 1년 저장된다 (헤더의 언어 전환 링크)</li>
 *   <li>쿠키가 없으면 브라우저 언어(Accept-Language)로 정한다 — 한국어가 아니면 영어, 헤더가 없으면 한국어</li>
 * </ul>
 * 문구: messages.properties(한국어) / messages_en.properties(영어)
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        return Messages.createMessageSource();
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang") {
            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                super.setLocale(request, response, locale == null ? null : supported(locale));
            }
        };
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setDefaultLocaleFunction(I18nConfig::fromBrowser);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }

    private static Locale fromBrowser(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return Messages.KOREAN;
        }
        return supported(request.getLocale());
    }

    /** 지원 언어로 맞춘다: 한국어가 아니면 영어 */
    private static Locale supported(Locale locale) {
        return Messages.KOREAN.getLanguage().equals(locale.getLanguage()) ? Messages.KOREAN : Messages.ENGLISH;
    }
}
