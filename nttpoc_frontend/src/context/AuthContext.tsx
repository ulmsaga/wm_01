import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getMe } from '@/api/auth/authApi';
import { connectSse, disconnectSse } from '@/api/sse/sseService';
import type { User } from '@/types';

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  loginAlert: string;
  loginUser: (userData: User) => void;
  logoutUser: () => void;
  clearLoginAlert: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loginAlert, setLoginAlert] = useState('');

  /**
   * 세션 무효화(중복 로그인) 시 호출.
   * - setUser(null) → ProtectedRoute가 /login으로 리다이렉트 (클라이언트 사이드)
   * - logout() 미호출: 서버가 이미 revokeAllSessions로 RT를 무효화했으므로 불필요.
   */
  function handleSessionInvalidated() {
    disconnectSse();
    setLoginAlert('다른 기기에서 로그인하여 현재 세션이 종료되었습니다.');
    setUser(null);
  }

  /**
   * SSE가 4xx(AT 만료 등)로 영구 종료 시 호출.
   * - AT 만료 → 인터셉터가 RT로 갱신 시도
   *   → RT 유효: 새 AT 발급 → getMe 성공 → SSE 재연결
   *   → RT 무효: 인터셉터가 /login으로 이동
   */
  async function handleSseError() {
    try {
      const res = await getMe();
      if (res?.data) {
        setUser(res.data);
        connectSse(handleSessionInvalidated, handleSseError);
      } else {
        setUser(null);
      }
    } catch (_) {
      setUser(null);
    }
  }

  // 앱 최초 로드 시 /auth/me 로 세션 복원
  useEffect(() => {
    getMe()
      .then((res) => {
        if (res?.data) {
          setUser(res.data);
          connectSse(handleSessionInvalidated, handleSseError);
        }
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => setIsLoading(false));

    return () => disconnectSse();
  }, []);

  function loginUser(userData: User) {
    setUser(userData);
    connectSse(handleSessionInvalidated, handleSseError);
  }

  function logoutUser() {
    disconnectSse();
    setUser(null);
  }

  function clearLoginAlert() {
    setLoginAlert('');
  }

  return (
    <AuthContext.Provider value={{ user, isLoading, loginUser, logoutUser, loginAlert, clearLoginAlert }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
