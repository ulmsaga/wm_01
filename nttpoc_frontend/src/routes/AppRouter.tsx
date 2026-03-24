import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from '@/pages/auth/LoginPage';
import OtpPage from '@/pages/auth/OtpPage';
import NwTopologyPage from '@/pages/nw/topology/NwTopologyPage';
import NwDigitalTwinPage from '@/pages/nw/digital-twin/NwDigitalTwinPage';
import ProtectedRoute from '@/routes/ProtectedRoute';
import MainLayout from '@/components/layout/MainLayout';

const DEFAULT_PATH = '/nw/monitoring/digital-twin';

function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/otp" element={<OtpPage />} />

      {/* NW トポロジー監視 */}
      <Route
        path="/nw/monitoring/topology"
        element={
          <ProtectedRoute>
            <MainLayout>
              <NwTopologyPage />
            </MainLayout>
          </ProtectedRoute>
        }
      />

      {/* 機器室 デジタルツイン */}
      <Route
        path="/nw/monitoring/digital-twin"
        element={
          <ProtectedRoute>
            <MainLayout>
              <NwDigitalTwinPage />
            </MainLayout>
          </ProtectedRoute>
        }
      />

      <Route path="/home" element={<Navigate to={DEFAULT_PATH} replace />} />
    </Routes>
  );
}

export default AppRouter;
