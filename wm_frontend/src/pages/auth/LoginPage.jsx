import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AuthLayout from '../../components/layout/AuthLayout';
import InputField from '../../components/common/InputField';
import Button from '../../components/common/Button';
import { login } from '../../api/auth/authApi';

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
    <AuthLayout title="로그인">
      <form onSubmit={handleSubmit}>
        <InputField
          id="loginId"
          name="loginId"
          label="ID"
          value={form.loginId}
          onChange={handleChange}
          placeholder="아이디 입력"
          autoComplete="username"
        />

        <InputField
          id="loginPw"
          name="loginPw"
          label="비밀번호"
          type="password"
          value={form.loginPw}
          onChange={handleChange}
          placeholder="비밀번호 입력"
          autoComplete="current-password"
        />

        {errorMsg ? <p className="error-text">{errorMsg}</p> : null}

        <Button type="submit" disabled={loading}>
          {loading ? '로그인 중...' : '로그인'}
        </Button>
      </form>
    </AuthLayout>
  );
}

export default LoginPage;