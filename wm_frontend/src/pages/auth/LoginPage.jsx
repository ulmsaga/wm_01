import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AuthLayout from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { login } from '@/api/auth/authApi';
import { useAuth } from '@/context/AuthContext';

function LoginPage() {
  const navigate = useNavigate();
  const { loginUser } = useAuth();

  const [form, setForm] = useState({
    loginId: '',
    loginPw: '',
  });

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  function handleChange(event) {
    const name = event.target.name;
    const value = event.target.value;

    setForm(function (prev) {
      return {
        ...prev,
        [name]: value,
      };
    });
  }

  async function handleSubmit(event) {
    event.preventDefault();
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
      });

      if (response && response.success) {
        if (response.data && response.data.requireSecondAuth) {
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
        setErrorMsg(response.message || '로그인에 실패했습니다.');
      }
    } catch (error) {
      if (error.response && error.response.data && error.response.data.message) {
        setErrorMsg(error.response.data.message);
      } else {
        setErrorMsg('로그인 처리 중 오류가 발생했습니다.');
      }
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <div className="flex justify-center items-center min-h-screen bg-muted dark:bg-slate-950 transition-colors">
        <form
          onSubmit={handleSubmit}
          className="w-[60vw] max-w-lg bg-card dark:bg-slate-900 rounded-xl shadow-lg dark:shadow-slate-900/50 p-8 flex flex-col gap-6 border border-border dark:border-slate-800 transition-colors"
        >
          <h2 className="text-lg font-semibold text-center mb-4 text-foreground dark:text-white">로그인</h2>
          <div className="flex flex-col gap-1">
            <label htmlFor="loginId" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">ID</label>
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
            <label htmlFor="loginPw" className="text-sm font-medium mb-1 text-foreground dark:text-slate-200">비밀번호</label>
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
          {errorMsg ? (
            <p className="text-destructive text-sm text-center mt-2">{errorMsg}</p>
          ) : null}
          <Button
            type="submit"
            disabled={loading}
            className="w-full h-8 text-base font-semibold bg-primary text-primary-foreground hover:bg-primary-dark dark:bg-primary-light dark:text-slate-900 dark:hover:bg-primary transition"
          >
            {loading ? '로그인 중...' : '로그인'}
          </Button>
        </form>
      </div>
    </AuthLayout>
  );
}

export default LoginPage;