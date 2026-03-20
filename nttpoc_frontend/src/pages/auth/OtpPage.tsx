import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import AuthLayout from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { verifyOtp } from '@/api/auth/authApi';
import { useAuth } from '@/context/AuthContext';

interface OtpLocationState {
  otpSeq?: number;
  sendType?: string;
  sendTarget?: string;
}

function OtpPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { loginUser } = useAuth();

  const state = (location.state ?? {}) as OtpLocationState;
  const { otpSeq, sendType, sendTarget } = state;

  const [otpCode, setOtpCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  // OTP 진입 데이터가 없으면 로그인으로 돌려보냄
  if (!otpSeq) {
    return <Navigate to="/login" replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setErrorMsg('');

    if (!otpCode || otpCode.length !== 6) {
      setErrorMsg('6자리 인증코드를 입력하세요.');
      return;
    }

    try {
      setLoading(true);
      const response = await verifyOtp(otpSeq, otpCode);

      if (response?.success && response.data) {
        loginUser(response.data);
        navigate('/home', { replace: true });
      } else {
        setErrorMsg(response?.message ?? '인증에 실패했습니다.');
      }
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      setErrorMsg(axiosError?.response?.data?.message ?? '인증 처리 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  const sendLabel = sendType === 'EMAIL' ? '이메일' : '휴대폰';

  return (
    <AuthLayout>
      <div className="flex justify-center items-center min-h-screen bg-muted dark:bg-slate-950 transition-colors">
        <form
          onSubmit={(e) => { void handleSubmit(e); }}
          className="w-[60vw] max-w-lg bg-card dark:bg-slate-900 rounded-xl shadow-lg dark:shadow-slate-900/50 p-8 flex flex-col gap-6 border border-border dark:border-slate-800 transition-colors"
        >
          <h2 className="text-lg font-semibold text-center mb-1 text-foreground dark:text-white">
            2차 인증
          </h2>
          <p className="text-sm text-muted-foreground dark:text-slate-400">
            {sendLabel}({sendTarget})으로 발송된 6자리 코드를 입력하세요.
          </p>
          <div className="flex flex-col gap-1">
            <label htmlFor="otpCode" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">
              인증코드
            </label>
            <Input
              id="otpCode"
              name="otpCode"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="6자리 숫자 입력"
              autoComplete="one-time-code"
              inputMode="numeric"
              maxLength={6}
              className="h-7 text-sm tracking-widest text-center bg-background dark:bg-slate-800 text-foreground dark:text-white border-border dark:border-slate-700"
            />
          </div>
          {errorMsg ? (
            <p className="text-destructive text-sm text-center mt-2">{errorMsg}</p>
          ) : null}
          <Button
            type="submit"
            disabled={loading}
            className="w-full h-8 text-base font-semibold bg-primary text-primary-foreground hover:bg-primary-dark dark:bg-primary-light dark:text-slate-900 dark:hover:bg-primary transition"
          >
            {loading ? '확인 중...' : '인증'}
          </Button>
          <button
            type="button"
            onClick={() => navigate('/login', { replace: true })}
            className="text-xs text-muted-foreground hover:underline text-center"
          >
            로그인으로 돌아가기
          </button>
        </form>
      </div>
    </AuthLayout>
  );
}

export default OtpPage;
