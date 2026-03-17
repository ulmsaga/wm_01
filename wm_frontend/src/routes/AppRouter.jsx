import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from '@/pages/auth/LoginPage';
import OtpPage from '@/pages/auth/OtpPage';
import ProtectedRoute from '@/routes/ProtectedRoute';

// TODO: HomePage 구현 후 아래 주석 해제
// import HomePage from '@/pages/home/HomePage';

function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/otp" element={<OtpPage />} />
      <Route
        path="/home"
        element={
          <ProtectedRoute>
            {/* <HomePage /> */}
            <div className="p-8 text-foreground">홈 화면 (준비 중)</div>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

export default AppRouter;
