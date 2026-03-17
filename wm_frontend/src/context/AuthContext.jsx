import { createContext, useContext, useEffect, useState } from 'react';
import { getMe } from '@/api/auth/authApi';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);       // { userSeq, userUid, userName }
  const [isLoading, setIsLoading] = useState(true); // 초기 세션 확인 중

  // 앱 최초 로드 시 /auth/me 로 세션 복원
  useEffect(() => {
    getMe()
      .then((res) => {
        if (res?.data) setUser(res.data);
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => setIsLoading(false));
  }, []);

  function loginUser(userData) {
    setUser(userData);
  }

  function logoutUser() {
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, isLoading, loginUser, logoutUser }}>
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
