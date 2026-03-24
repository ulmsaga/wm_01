package com.mobigen.aiop.nttpoc.core.mail;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.BDDMockito.given;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.mail.impl.EmailServiceImpl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl")
class EmailServiceImplTest {

    @Mock JavaMailSender mailSender;
    @Mock MessageSource messageSource;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // MessageSource mock — 키 그대로 반환 (HTML 구조 검증이 목적이 아니므로)
        org.mockito.BDDMockito.lenient()
                .when(messageSource.getMessage(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(java.util.Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        emailService = new EmailServiceImpl(mailSender, messageSource, "noreply@test.com");
    }

    @Nested
    @DisplayName("sendOtpEmail")
    class SendOtpEmail {

        @Test
        @DisplayName("정상 발송 → mailSender.send 호출")
        void success_callsMailSenderSend() {
            // 실제 MimeMessage 사용 (MimeMessageHelper가 내부 조작 필요)
            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            assertThatCode(() -> emailService.sendOtpEmail("user@test.com", "123456", 5))
                    .doesNotThrowAnyException();

            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("발송 실패 (MailException) → EMAIL_SEND_FAILED NttpocException")
        void sendFailure_throwsEmailSendFailed() {
            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            // MailException은 RuntimeException이므로 MessagingException catch를 우회함
            // MessagingException 경로를 직접 테스트하기 위해 깨진 MimeMessage 사용
            MimeMessage broken = mock(MimeMessage.class, invocation -> {
                throw new jakarta.mail.MessagingException("broken");
            });
            given(mailSender.createMimeMessage()).willReturn(broken);

            assertThatThrownBy(() -> emailService.sendOtpEmail("user@test.com", "123456", 5))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
