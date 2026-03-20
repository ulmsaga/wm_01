import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from '@/pages/auth/LoginPage';
import OtpPage from '@/pages/auth/OtpPage';
import HomePage from '@/pages/home/HomePage';
import ProtectedRoute from '@/routes/ProtectedRoute';
import MainLayout from '@/components/layout/MainLayout';

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
            <MainLayout>
              <HomePage />
            </MainLayout>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

export default AppRouter;
