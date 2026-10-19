package com.mlbgame.common;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * 자바 코드에서 쓰는 다국어 문구. 문구는 messages.properties(한국어, 기본) / messages_en.properties(영어)에 있다.
 *
 * <ul>
 *   <li>{@link #get}: 키로 문구를 찾는다 — 예외 메시지, 플래시 메시지, 하이라이트 문장 등</li>
 *   <li>{@link #text}: 한국어는 코드에 그대로 두고 영어만 messages_en 에서 찾는다 — enum 표시명용</li>
 * </ul>
 * 언어는 요청마다 정해진다(쿠키·Accept-Language, {@code I18nConfig}). 요청 밖(테스트·배치)에서는 한국어.
 */
@Component
public class Messages {

    public static final Locale KOREAN = Locale.KOREAN;
    public static final Locale ENGLISH = Locale.ENGLISH;

    private static volatile MessageSource source;

    public Messages(MessageSource messageSource) {
        source = messageSource;
    }

    /** 현재 요청의 언어. 요청 밖이면 한국어 */
    public static Locale locale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        Locale locale = context == null ? null : context.getLocale();
        return locale != null && ENGLISH.getLanguage().equals(locale.getLanguage()) ? ENGLISH : KOREAN;
    }

    public static boolean english() {
        return locale() == ENGLISH;
    }

    /** 키에 해당하는 문구. {0}, {1} … 자리에 args 가 들어간다 */
    public static String get(String key, Object... args) {
        return source().getMessage(key, args, key, locale());
    }

    /** enum 표시명: 한국어면 koText 그대로, 영어면 messages_en 의 key (없으면 koText) */
    public static String text(String key, String koText) {
        if (!english()) {
            return koText;
        }
        return source().getMessage(key, null, koText, ENGLISH);
    }

    /** 월 표시: "5월" / "May" */
    public static String month(int month) {
        return english() ? Month.of(month).getDisplayName(TextStyle.FULL, ENGLISH) : month + "월";
    }

    private static MessageSource source() {
        MessageSource current = source;
        if (current == null) {   // 스프링 없이 도는 단위 테스트
            current = createMessageSource();
            source = current;
        }
        return current;
    }

    /** 앱 전체가 쓰는 MessageSource. 숫자 인자는 그대로 문자열로 넣는다 (2026 → "2,026" 방지) */
    public static ResourceBundleMessageSource createMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource() {
            @Override
            protected Object[] resolveArguments(Object[] args, Locale locale) {
                Object[] resolved = super.resolveArguments(args, locale);
                if (resolved == null) {
                    return new Object[0];
                }
                return Arrays.stream(resolved).map(a -> a instanceof Number ? String.valueOf(a) : a).toArray();
            }
        };
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);   // 한국어는 messages.properties 로
        return messageSource;
    }
}
