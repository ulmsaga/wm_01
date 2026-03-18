import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import AuthLayout from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { login } from '@/api/auth/authApi';
import { disconnectSse } from '@/api/sse/sseService';
import { useAuth } from '@/context/AuthContext';

/** 중복 로그인 확인 모달 */
function ConfirmDialog({ message, onConfirm, onCancel }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-card dark:bg-slate-900 rounded-xl shadow-xl p-6 w-90 flex flex-col gap-4 border border-border dark:border-slate-700">
        <h3 className="font-semibold text-base text-foreground dark:text-white">중복 로그인 확인</h3>
        <p className="text-sm text-muted-foreground dark:text-slate-300 whitespace-pre-line">{message}</p>
        <div className="flex gap-2 justify-end">
          <Button variant="outline" size="sm" onClick={onCancel}>취소</Button>
          <Button variant="destructive" size="sm" onClick={onConfirm}>기존 세션 종료 후 로그인</Button>
        </div>
      </div>
    </div>
  );
}

/** 인라인 알림 배너 */
function AlertBanner({ message, type = 'info', onClose }) {
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

function LoginPage() {
  const navigate = useNavigate();
  const { loginUser, loginAlert, clearLoginAlert } = useAuth();

  const [form, setForm] = useState({ loginId: '', loginPw: '' });
  const [saveId, setSaveId] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [infoMsg, setInfoMsg] = useState('');
  const [confirmDialog, setConfirmDialog] = useState(null); // { message } | null

  // 강제 로그아웃 알림 (AuthContext에서 전달) + 저장된 ID 복원
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

  function handleChange(e) {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (name === 'loginId' && saveId) {
      localStorage.setItem('savedLoginId', value);
    }
  }

  function handleSaveIdChange(e) {
    const checked = e.target.checked;
    setSaveId(checked);
    if (checked) {
      localStorage.setItem('savedLoginId', form.loginId);
    } else {
      localStorage.removeItem('savedLoginId');
    }
  }

  async function handleSubmit(event, forceLogin = false) {
    if (event) event.preventDefault();
    setErrorMsg('');

    if (!form.loginId || !form.loginPw) {
      setErrorMsg('ID와 비밀번호를 입력하세요.');
      return;
    }

    try {
      setLoading(true);

      const response = await login({
        loginId: form.loginId,
        loginPw: form.loginPw,
        forceLogin,
      });

      if (response?.success) {
        if (response.data?.requireDuplicateConfirm) {
          // window.confirm 대신 커스텀 모달로 표시
          setConfirmDialog({ message: response.data.message });
        } else if (response.data?.requireSecondAuth) {
          navigate('/otp', {
            state: {
              otpSeq: response.data.otpSeq,
              sendType: response.data.sendType,
              sendTarget: response.data.sendTarget,
            },
          });
        } else {
          loginUser(response.data);
          navigate('/home', { replace: true });
        }
      } else {
        setErrorMsg(response?.message || '로그인에 실패했습니다.');
      }
    } catch (error) {
      setErrorMsg(
        error?.response?.data?.message || '로그인 처리 중 오류가 발생했습니다.'
      );
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  function handleConfirmLogin() {
    setConfirmDialog(null);
    disconnectSse(); // 서버 kick 이벤트가 현재 창으로 수신되지 않도록 차단
    handleSubmit(null, true);
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
          onSubmit={handleSubmit}
          className="w-[60vw] max-w-lg bg-card dark:bg-slate-900 rounded-xl shadow-lg dark:shadow-slate-900/50 p-8 flex flex-col gap-6 border border-border dark:border-slate-800 transition-colors"
        >
          <h2 className="text-lg font-semibold text-center text-foreground dark:text-white">로그인</h2>

          <div className="flex flex-col gap-1">
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="loginId" className="text-sm font-medium text-foreground dark:text-slate-200">
                ID
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={saveId}
                  onChange={handleSaveIdChange}
                  className="w-3.5 h-3.5 accent-primary"
                />
                <span className="text-xs text-muted-foreground">아이디 저장</span>
              </label>
            </div>
            <Input
              id="loginId"
              name="loginId"
              value={form.loginId}
              onChange={handleChange}
              placeholder="아이디 입력"
              autoComplete="username"
              className="h-7 text-sm bg-background dark:bg-slate-800 text-foreground dark:text-white border-border dark:border-slate-700 placeholder:text-muted-foreground"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="loginPw" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">
              비밀번호
            </label>
            <Input
              id="loginPw"
              name="loginPw"
              type="password"
              value={form.loginPw}
              onChange={handleChange}
              placeholder="비밀번호 입력"
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
            {loading ? '로그인 중...' : '로그인'}
          </Button>

          {infoMsg && <AlertBanner message={infoMsg} type="error" onClose={() => setInfoMsg('')} />}
        </form>
      </div>
    </AuthLayout>
  );
}

export default LoginPage;
