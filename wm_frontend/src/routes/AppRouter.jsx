import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from '@/pages/auth/LoginPage';
// import OtpPage from 'src/pages/OtpPage';
// import HomePage from 'src/pages/HomePage';

function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      {/* <Route path="/otp" element={<OtpPage />} />
      <Route path="/home" element={<HomePage />} /> */}
    </Routes>
  );
}

export default AppRouter;