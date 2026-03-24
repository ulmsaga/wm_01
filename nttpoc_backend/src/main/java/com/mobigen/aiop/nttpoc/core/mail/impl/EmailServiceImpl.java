package com.mobigen.aiop.nttpoc.core.mail.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.mail.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.Locale;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final String from;

    public EmailServiceImpl(JavaMailSender mailSender,
                            MessageSource messageSource,
                            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.messageSource = messageSource;
        this.from = from;
    }

    @Override
    public void sendOtpEmail(String to, String otpCode, int expireMinutes) {
        // 요청 스레드의 Locale (AcceptHeaderLocaleResolver 가 Accept-Language 헤더로 설정)
        Locale locale = LocaleContextHolder.getLocale();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(msg("email.otp.subject", locale));
            helper.setText(buildOtpHtml(otpCode, expireMinutes, locale), true);

            mailSender.send(message);
            log.info("[Email] OTP 발송 완료 to={} locale={}", to, locale.getLanguage());

        } catch (MessagingException e) {
            log.error("[Email] OTP 발송 실패 to={}", to, e);
            throw new NttpocException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String buildOtpHtml(String otpCode, int expireMinutes, Locale locale) {
        String headerTitle = msg("email.otp.header.title", locale);
        String greeting    = msg("email.otp.greeting",     locale);
        String body        = msg("email.otp.body",         locale);
        String expiry      = msg("email.otp.expiry",       locale, expireMinutes);
        String ignore      = msg("email.otp.ignore",       locale);
        String warning     = msg("email.otp.warning",      locale);

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:'Apple SD Gothic Neo',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="480" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;
                                      box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                          <!-- 헤더 -->
                          <tr>
                            <td style="background:#7c3aed;padding:32px 40px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;
                                         letter-spacing:-0.5px;">%s</h1>
                            </td>
                          </tr>

                          <!-- 본문 -->
                          <tr>
                            <td style="padding:40px 40px 24px;">
                              <p style="margin:0 0 8px;color:#374151;font-size:15px;line-height:1.6;">
                                %s
                              </p>
                              <p style="margin:0 0 28px;color:#374151;font-size:15px;line-height:1.6;">
                                %s
                              </p>

                              <!-- OTP 코드 박스 -->
                              <div style="background:#f5f3ff;border:2px solid #7c3aed;border-radius:10px;
                                          padding:24px;text-align:center;margin-bottom:28px;">
                                <span style="font-size:36px;font-weight:800;letter-spacing:10px;
                                             color:#7c3aed;font-family:monospace;">%s</span>
                              </div>

                              <!-- 유효 시간 -->
                              <p style="margin:0 0 8px;color:#6b7280;font-size:13px;text-align:center;">
                                %s
                              </p>
                              <p style="margin:0 0 28px;color:#6b7280;font-size:13px;text-align:center;">
                                %s
                              </p>
                            </td>
                          </tr>

                          <!-- 보안 경고 -->
                          <tr>
                            <td style="background:#fef9c3;padding:16px 40px;border-top:1px solid #fde68a;">
                              <p style="margin:0;color:#92400e;font-size:12px;line-height:1.6;">
                                %s
                              </p>
                            </td>
                          </tr>

                          <!-- 푸터 -->
                          <tr>
                            <td style="padding:20px 40px;text-align:center;border-top:1px solid #e5e7eb;">
                              <p style="margin:0;color:#9ca3af;font-size:11px;">
                                &copy; NTT. All rights reserved.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(headerTitle, greeting, body, otpCode, expiry, ignore, warning);
    }

    private String msg(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }
}
