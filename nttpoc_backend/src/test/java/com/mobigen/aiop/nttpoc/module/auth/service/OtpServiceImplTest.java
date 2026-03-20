package com.mobigen.aiop.nttpoc.module.auth.service;

import com.mobigen.aiop.nttpoc.core.exception.ErrorCode;
import com.mobigen.aiop.nttpoc.core.exception.NttpocException;
import com.mobigen.aiop.nttpoc.core.mail.EmailService;
import com.mobigen.aiop.nttpoc.module.auth.dao.OtpDao;
import com.mobigen.aiop.nttpoc.module.auth.service.impl.OtpServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpServiceImpl")
class OtpServiceImplTest {

    @Mock OtpDao otpDao;
    @Mock EmailService emailService;
    @InjectMocks OtpServiceImpl otpService;

    // ── createAndSendOtp ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAndSendOtp")
    class CreateAndSendOtp {

        @Test
        @DisplayName("EMAIL 타입 → otpDao.insertOtp + emailService.sendOtpEmail 호출 후 otpSeq 반환")
        void emailType_insertsAndSendsEmail() {
            willAnswer(inv -> {
                Map<String, Object> p = inv.getArgument(0);
                p.put("otpSeq", 42L);
                return null;
            }).given(otpDao).insertOtp(any());

            long seq = otpService.createAndSendOtp(1L, "EMAIL", "test@test.com");

            assertThat(seq).isEqualTo(42L);
            verify(emailService).sendOtpEmail(eq("test@test.com"), anyString(), eq(5));
        }

        @Test
        @DisplayName("SMS 타입 → NttpocException(SMS_NOT_SUPPORTED)")
        void smsType_throwsSmsNotSupported() {
            willAnswer(inv -> {
                Map<String, Object> p = inv.getArgument(0);
                p.put("otpSeq", 10L);
                return null;
            }).given(otpDao).insertOtp(any());

            assertThatThrownBy(() -> otpService.createAndSendOtp(1L, "SMS", "010-0000-0000"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SMS_NOT_SUPPORTED);
        }

        @Test
        @DisplayName("OTP 코드는 6자리 숫자")
        void otpCode_isSixDigits() {
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            willAnswer(inv -> {
                Map<String, Object> p = inv.getArgument(0);
                p.put("otpSeq", 1L);
                return null;
            }).given(otpDao).insertOtp(captor.capture());

            otpService.createAndSendOtp(1L, "EMAIL", "test@test.com");

            String code = (String) captor.getValue().get("otpCode");
            assertThat(code).matches("\\d{6}");
        }
    }

    // ── verifyOtp ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtp {

        private Map<String, Object> validOtp(String code) {
            Map<String, Object> otp = new HashMap<>();
            otp.put("VERIFIED_YN", "N");
            otp.put("EXPIRED_AT", LocalDateTime.now().plusMinutes(5));
            otp.put("FAIL_COUNT", 0);
            otp.put("MAX_FAIL_COUNT", 3);
            otp.put("OTP_CODE", code);
            otp.put("USER_SEQ", 1L);
            return otp;
        }

        @Test
        @DisplayName("정상 코드 → userSeq 반환")
        void validCode_returnsUserSeq() {
            given(otpDao.selectOtpBySeq(1L)).willReturn(validOtp("123456"));

            long userSeq = otpService.verifyOtp(1L, "123456");

            assertThat(userSeq).isEqualTo(1L);
            verify(otpDao).updateOtpVerified(1L);
        }

        @Test
        @DisplayName("OTP 없음 → OTP_NOT_FOUND 예외")
        void otpNotFound_throwsException() {
            given(otpDao.selectOtpBySeq(99L)).willReturn(null);

            assertThatThrownBy(() -> otpService.verifyOtp(99L, "000000"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 인증된 OTP → OTP_ALREADY_USED 예외")
        void alreadyUsed_throwsException() {
            Map<String, Object> otp = validOtp("123456");
            otp.put("VERIFIED_YN", "Y");
            given(otpDao.selectOtpBySeq(1L)).willReturn(otp);

            assertThatThrownBy(() -> otpService.verifyOtp(1L, "123456"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_ALREADY_USED);
        }

        @Test
        @DisplayName("만료된 OTP → OTP_EXPIRED 예외")
        void expired_throwsException() {
            Map<String, Object> otp = validOtp("123456");
            otp.put("EXPIRED_AT", LocalDateTime.now().minusMinutes(1));
            given(otpDao.selectOtpBySeq(1L)).willReturn(otp);

            assertThatThrownBy(() -> otpService.verifyOtp(1L, "123456"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_EXPIRED);
        }

        @Test
        @DisplayName("실패 횟수 초과 → OTP_MAX_FAILED 예외")
        void maxFailed_throwsException() {
            Map<String, Object> otp = validOtp("123456");
            otp.put("FAIL_COUNT", 3);
            given(otpDao.selectOtpBySeq(1L)).willReturn(otp);

            assertThatThrownBy(() -> otpService.verifyOtp(1L, "000000"))
                    .isInstanceOf(NttpocException.class)
                    .extracting(e -> ((NttpocException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_MAX_FAILED);
        }

        @Test
        @DisplayName("코드 불일치 → OTP_MISMATCH + failCount 증가 + 남은 횟수 args 포함")
        void codeMismatch_throwsWithRemainingCount() {
            given(otpDao.selectOtpBySeq(1L)).willReturn(validOtp("123456"));

            assertThatThrownBy(() -> otpService.verifyOtp(1L, "000000"))
                    .isInstanceOf(NttpocException.class)
                    .satisfies(e -> {
                        NttpocException we = (NttpocException) e;
                        assertThat(we.getErrorCode()).isEqualTo(ErrorCode.OTP_MISMATCH);
                        assertThat(we.getArgs()).containsExactly(2); // maxFail(3) - fail(0) - 1 = 2
                    });
            verify(otpDao).incrementOtpFailCount(1L);
        }
    }
}
