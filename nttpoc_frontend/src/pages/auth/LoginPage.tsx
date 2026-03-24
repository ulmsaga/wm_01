import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import AuthLayout from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { login } from '@/api/auth/authApi';
import { disconnectSse } from '@/api/sse/sseService';
import { useAuth } from '@/context/AuthContext';
import LanguageSelector from '@/components/common/LanguageSelector';

interface ConfirmDialogProps {
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}

function ConfirmDialog({ message, onConfirm, onCancel }: ConfirmDialogProps) {
  const { t } = useTranslation('auth');
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-card dark:bg-slate-900 rounded-xl shadow-xl p-6 w-90 flex flex-col gap-4 border border-border dark:border-slate-700">
        <h3 className="font-semibold text-base text-foreground dark:text-white">{t('login.duplicate.title')}</h3>
        <p className="text-sm text-muted-foreground dark:text-slate-300 whitespace-pre-line">{message}</p>
        <div className="flex gap-2 justify-end">
          <Button variant="outline" size="sm" onClick={onCancel}>{t('action.cancel', { ns: 'common' })}</Button>
          <Button variant="destructive" size="sm" onClick={onConfirm}>{t('login.duplicate.confirm')}</Button>
        </div>
      </div>
    </div>
  );
}

type AlertType = 'info' | 'error';

interface AlertBannerProps {
  message: string;
  type?: AlertType;
  onClose?: () => void;
}

function AlertBanner({ message, type = 'info', onClose }: AlertBannerProps) {
  const styles =
    type === 'info'
      ? 'bg-blue-50 dark:bg-blue-950 border-blue-200 dark:border-blue-800 text-blue-700 dark:text-blue-300'
      : 'bg-red-50 dark:bg-red-950 border-red-200 dark:border-red-800 text-red-700 dark:text-red-300';
  return (
    <div className={`flex items-start justify-between rounded-lg border px-4 py-2.5 text-sm ${styles}`}>
      <span>{message}</span>
      {onClose && (
        <button type="button" onClick={onClose} className="ml-2 opacity-60 hover:opacity-100 leading-none">
          ✕
        </button>
      )}
    </div>
  );
}

interface FormState {
  loginId: string;
  loginPw: string;
}

function LoginPage() {
  const navigate = useNavigate();
  const { loginUser, loginAlert, clearLoginAlert } = useAuth();
  const { t, i18n } = useTranslation('auth');

  const [form, setForm] = useState<FormState>({ loginId: '', loginPw: '' });
  const [saveId, setSaveId] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [infoMsg, setInfoMsg] = useState('');
  const [confirmDialog, setConfirmDialog] = useState<{ message: string } | null>(null);

  useEffect(() => { setErrorMsg(''); }, [i18n.language]);

  useEffect(() => {
    if (loginAlert) {
      setInfoMsg(loginAlert);
      clearLoginAlert();
    }
  }, [loginAlert]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const savedId = localStorage.getItem('savedLoginId');
    if (savedId) {
      setForm((prev) => ({ ...prev, loginId: savedId }));
      setSaveId(true);
    }
  }, []);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (name === 'loginId' && saveId) {
      localStorage.setItem('savedLoginId', value);
    }
  }

  function handleSaveIdChange(e: React.ChangeEvent<HTMLInputElement>) {
    const checked = e.target.checked;
    setSaveId(checked);
    if (checked) {
      localStorage.setItem('savedLoginId', form.loginId);
    } else {
      localStorage.removeItem('savedLoginId');
    }
  }

  async function handleSubmit(event: FormEvent | null, forceLogin = false) {
    event?.preventDefault();
    setErrorMsg('');

    if (!form.loginId || !form.loginPw) {
      setErrorMsg(t('login.error.required'));
      return;
    }

    try {
      setLoading(true);
      const response = await login({ loginId: form.loginId, loginPw: form.loginPw, forceLogin });

      if (response?.success) {
        if (response.data?.requireDuplicateConfirm) {
          setConfirmDialog({ message: response.data.message ?? '' });
        } else if (response.data?.requireSecondAuth) {
          navigate('/otp', {
            state: {
              otpSeq: response.data.otpSeq,
              sendType: response.data.sendType,
              sendTarget: response.data.sendTarget,
            },
          });
        } else {
          loginUser({
            userSeq: response.data!.userSeq!,
            userId: response.data!.userId!,
            userName: response.data!.userName!,
          });
          navigate('/nw/monitoring/digital-twin', { replace: true });
        }
      } else {
        setErrorMsg(response?.message ?? t('login.error.failed'));
      }
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      setErrorMsg(axiosError?.response?.data?.message ?? t('login.error.network'));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  function handleConfirmLogin() {
    setConfirmDialog(null);
    disconnectSse();
    void handleSubmit(null, true);
  }

  return (
    <AuthLayout>
      {confirmDialog && (
        <ConfirmDialog
          message={confirmDialog.message}
          onConfirm={handleConfirmLogin}
          onCancel={() => setConfirmDialog(null)}
        />
      )}

      <div className="flex justify-center items-center min-h-screen bg-muted dark:bg-slate-950 transition-colors">
        <form
          onSubmit={(e) => { void handleSubmit(e); }}
          className="w-[60vw] max-w-lg bg-card dark:bg-slate-900 rounded-xl shadow-lg dark:shadow-slate-900/50 p-8 flex flex-col gap-6 border border-border dark:border-slate-800 transition-colors"
        >
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-foreground dark:text-white">{t('login.title')}</h2>
            <LanguageSelector />
          </div>

          <div className="flex flex-col gap-1">
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="loginId" className="text-sm font-medium text-foreground dark:text-slate-200">
                {t('login.id')}
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={saveId}
                  onChange={handleSaveIdChange}
                  className="w-3.5 h-3.5 accent-primary"
                />
                <span className="text-xs text-muted-foreground">{t('login.saveId')}</span>
              </label>
            </div>
            <Input
              id="loginId"
              name="loginId"
              value={form.loginId}
              onChange={handleChange}
              placeholder={t('login.idPlaceholder')}
              autoComplete="username"
              className="h-7 text-sm bg-background dark:bg-slate-800 text-foreground dark:text-white border-border dark:border-slate-700 placeholder:text-muted-foreground"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="loginPw" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">
              {t('login.password')}
            </label>
            <Input
              id="loginPw"
              name="loginPw"
              type="password"
              value={form.loginPw}
              onChange={handleChange}
              placeholder={t('login.passwordPlaceholder')}
              autoComplete="current-password"
              className="h-7 text-sm bg-background dark:bg-slate-800 text-foreground dark:text-white border-border dark:border-slate-700 placeholder:text-muted-foreground"
            />
          </div>

          {errorMsg && <AlertBanner message={errorMsg} type="error" />}

          <Button
            type="submit"
            disabled={loading}
            className="w-full h-8 text-base font-semibold bg-primary text-primary-foreground hover:bg-primary-dark dark:bg-primary-light dark:text-slate-900 dark:hover:bg-primary transition"
          >
            {loading ? t('login.loading') : t('login.submit')}
          </Button>

          {infoMsg && <AlertBanner message={infoMsg} type="error" onClose={() => setInfoMsg('')} />}

          {i18n.language !== 'ja' && (
            <p className="text-xs text-muted-foreground leading-relaxed">
              ⓘ {t('contentLangNotice')}
            </p>
          )}
        </form>
      </div>
    </AuthLayout>
  );
}

export default LoginPage;
