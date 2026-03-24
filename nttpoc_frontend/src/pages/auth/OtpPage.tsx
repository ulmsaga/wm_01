import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation('auth');

  const state = (location.state ?? {}) as OtpLocationState;
  const { otpSeq, sendType, sendTarget } = state;

  const [otpCode, setOtpCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  if (!otpSeq) {
    return <Navigate to="/login" replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setErrorMsg('');

    if (!otpCode || otpCode.length !== 6) {
      setErrorMsg(t('otp.error.required'));
      return;
    }

    try {
      setLoading(true);
      const response = await verifyOtp(otpSeq, otpCode);

      if (response?.success && response.data) {
        loginUser(response.data);
        navigate('/nw/monitoring/digital-twin', { replace: true });
      } else {
        setErrorMsg(response?.message ?? t('otp.error.failed'));
      }
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      setErrorMsg(axiosError?.response?.data?.message ?? t('otp.error.network'));
    } finally {
      setLoading(false);
    }
  }

  const sendLabel = sendType === 'EMAIL' ? t('otp.sendType.email') : t('otp.sendType.sms');

  return (
    <AuthLayout>
      <div className="flex justify-center items-center min-h-screen bg-muted dark:bg-slate-950 transition-colors">
        <form
          onSubmit={(e) => { void handleSubmit(e); }}
          className="w-[60vw] max-w-lg bg-card dark:bg-slate-900 rounded-xl shadow-lg dark:shadow-slate-900/50 p-8 flex flex-col gap-6 border border-border dark:border-slate-800 transition-colors"
        >
          <h2 className="text-lg font-semibold text-center mb-1 text-foreground dark:text-white">
            {t('otp.title')}
          </h2>
          <p className="text-sm text-muted-foreground dark:text-slate-400">
            {t('otp.description', { sendLabel, sendTarget })}
          </p>
          <div className="flex flex-col gap-1">
            <label htmlFor="otpCode" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">
              {t('otp.code')}
            </label>
            <Input
              id="otpCode"
              name="otpCode"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder={t('otp.placeholder')}
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
            {loading ? t('otp.loading') : t('otp.submit')}
          </Button>
          <button
            type="button"
            onClick={() => navigate('/login', { replace: true })}
            className="text-xs text-muted-foreground hover:underline text-center"
          >
            {t('otp.backToLogin')}
          </button>
        </form>
      </div>
    </AuthLayout>
  );
}

export default OtpPage;
