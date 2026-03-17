package com.saga.wm.core.mail.impl;

import com.saga.wm.core.exception.ErrorCode;
import com.saga.wm.core.exception.WmException;
import com.saga.wm.core.mail.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendOtpEmail(String to, String otpCode, int expireMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("[WM] 로그인 인증 코드 안내");
            helper.setText(buildOtpHtml(otpCode, expireMinutes), true);

            mailSender.send(message);
            log.info("[Email] OTP 발송 완료 to={}", to);

        } catch (MessagingException e) {
            log.error("[Email] OTP 발송 실패 to={}", to, e);
            throw new WmException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String buildOtpHtml(String otpCode, int expireMinutes) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
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
                                         letter-spacing:-0.5px;">WM 인증 코드</h1>
                            </td>
                          </tr>

                          <!-- 본문 -->
                          <tr>
                            <td style="padding:40px 40px 24px;">
                              <p style="margin:0 0 8px;color:#374151;font-size:15px;line-height:1.6;">
                                안녕하세요.
                              </p>
                              <p style="margin:0 0 28px;color:#374151;font-size:15px;line-height:1.6;">
                                로그인 2차 인증을 위한 코드입니다.<br>
                                아래 코드를 인증 화면에 입력해 주세요.
                              </p>

                              <!-- OTP 코드 박스 -->
                              <div style="background:#f5f3ff;border:2px solid #7c3aed;border-radius:10px;
                                          padding:24px;text-align:center;margin-bottom:28px;">
                                <span style="font-size:36px;font-weight:800;letter-spacing:10px;
                                             color:#7c3aed;font-family:monospace;">%s</span>
                              </div>

                              <!-- 유효 시간 -->
                              <p style="margin:0 0 8px;color:#6b7280;font-size:13px;text-align:center;">
                                이 코드는 발급 후 <strong style="color:#374151;">%d분</strong> 동안 유효합니다.
                              </p>
                              <p style="margin:0 0 28px;color:#6b7280;font-size:13px;text-align:center;">
                                코드를 요청하지 않으셨다면 이 이메일을 무시해 주세요.
                              </p>
                            </td>
                          </tr>

                          <!-- 보안 경고 -->
                          <tr>
                            <td style="background:#fef9c3;padding:16px 40px;border-top:1px solid #fde68a;">
                              <p style="margin:0;color:#92400e;font-size:12px;line-height:1.6;">
                                ⚠ 이 코드는 본인만 사용하세요. WM 직원은 절대 인증 코드를 요청하지 않습니다.
                              </p>
                            </td>
                          </tr>

                          <!-- 푸터 -->
                          <tr>
                            <td style="padding:20px 40px;text-align:center;border-top:1px solid #e5e7eb;">
                              <p style="margin:0;color:#9ca3af;font-size:11px;">
                                &copy; WM. All rights reserved.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(otpCode, expireMinutes);
    }
}
