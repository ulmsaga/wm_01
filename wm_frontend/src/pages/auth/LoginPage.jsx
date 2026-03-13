import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AuthLayout from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { login } from '@/api/auth/authApi';

function LoginPage() {
  const navigate = useNavigate();

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
        if (response.data && response.data.needOtp) {
          navigate('/otp', {
            state: {
              loginId: form.loginId,
            },
          });
        } else {
          navigate('/home');
        }
      } else {
        setErrorMsg(response.message || '로그인에 실패했습니다.');
      }
    } catch (error) {
      setErrorMsg('로그인 처리 중 오류가 발생했습니다.');
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <div className="flex justify-center items-center min-h-screen">
        <form
          onSubmit={handleSubmit}
          className="w-[60vw] max-w-md bg-white rounded-xl shadow-lg p-8 flex flex-col gap-6"
        >
          <h2 className="text-lg font-semibold text-center mb-4">로그인</h2>
          <div className="flex flex-col gap-1">
            <label htmlFor="loginId" className="text-sm font-medium mb-1">ID</label>
            <Input
              id="loginId"
              name="loginId"
              value={form.loginId}
              onChange={handleChange}
              placeholder="아이디 입력"
              autoComplete="username"
              className="h-7 text-sm"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="loginPw" className="text-sm font-medium mb-1">비밀번호</label>
            <Input
              id="loginPw"
              name="loginPw"
              type="password"
              value={form.loginPw}
              onChange={handleChange}
              placeholder="비밀번호 입력"
              autoComplete="current-password"
              className="h-7 text-sm"
            />
          </div>
          {errorMsg ? (
            <p className="text-destructive text-sm text-center mt-2">{errorMsg}</p>
          ) : null}
          <Button
            type="submit"
            disabled={loading}
            className="w-full h-8 text-base font-semibold bg-primary text-primary-foreground hover:bg-primary/80 transition"
          >
            {loading ? '로그인 중...' : '로그인'}
          </Button>
        </form>
      </div>
    </AuthLayout>
  );
}

export default LoginPage;