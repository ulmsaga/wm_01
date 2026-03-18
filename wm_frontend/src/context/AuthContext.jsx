import { createContext, useContext, useEffect, useState } from 'react';
import { getMe } from '@/api/auth/authApi';
import { connectSse, disconnectSse } from '@/api/sse/sseService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);            // { userSeq, userUid, userName }
  const [isLoading, setIsLoading] = useState(true);  // 초기 세션 확인 중
  const [loginAlert, setLoginAlert] = useState('');  // 강제 로그아웃 알림 메시지

  /**
   * 세션 무효화(중복 로그인) 시 호출.
   * - sessionStorage/window.location 대신 React 상태로 메시지 전달 → 타이밍 race condition 없음
   * - setUser(null) → ProtectedRoute가 /login으로 리다이렉트 (클라이언트 사이드)
   * - logout() 미호출: 서버가 이미 revokeAllSessions로 RT를 무효화했으므로 불필요.
   *   (호출 시 동일 브라우저 다른 탭의 새 AT/RT(공유 쿠키)를 잘못 revoke하는 버그 발생)
   * - 무한루프 방지: JwtCookieAuthFilter의 isRevokedAfter 체크가 구(舊) AT 요청을 401로 차단
   */
  function handleSessionInvalidated() {
    disconnectSse();
    setLoginAlert('다른 기기에서 로그인하여 현재 세션이 종료되었습니다.');
    setUser(null); // → ProtectedRoute → <Navigate to="/login" />
  }

  /**
   * SSE가 4xx(AT 만료 등)로 영구 종료 시 호출.
   * - AT 만료 → 인터셉터가 RT로 갱신 시도
   *   → RT 유효: 새 AT 발급 → getMe 성공 → SSE 재연결
   *   → RT 무효(서버 재기동 cleanup): 인터셉터가 /login으로 이동
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

  function loginUser(userData) {
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
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
