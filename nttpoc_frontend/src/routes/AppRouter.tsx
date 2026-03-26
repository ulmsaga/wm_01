import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage        from '@/pages/auth/LoginPage';
import OtpPage          from '@/pages/auth/OtpPage';
import ProtectedRoute   from '@/routes/ProtectedRoute';
import MainLayout       from '@/components/layout/MainLayout';

// 실제 페이지 (구현 예정)
import NwDigitalTwinPage from '@/pages/nw/digital-twin/NwDigitalTwinPage';
import NwTopologyPage    from '@/pages/nw/topology/NwTopologyPage';

// DEMO 페이지
import NwDigitalTwinDemoPage from '@/pages/nw/demo/digital-twin/NwDigitalTwinDemoPage';
import NwTopologyDemoPage    from '@/pages/nw/demo/topology/NwTopologyDemoPage';

const DEFAULT_PATH = '/nw/monitoring/digital-twin';

function wrap(element: React.ReactNode) {
  return (
    <ProtectedRoute>
      <MainLayout>{element}</MainLayout>
    </ProtectedRoute>
  );
}

function AppRouter() {
  return (
    <Routes>
      <Route path="/"      element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/otp"   element={<OtpPage />} />

      {/* ── NW 실제 페이지 ─────────────────────────────────── */}
      <Route path="/nw/monitoring/digital-twin" element={wrap(<NwDigitalTwinPage />)} />
      <Route path="/nw/monitoring/topology"     element={wrap(<NwTopologyPage />)} />

      {/* ── NW DEMO 페이지 ────────────────────────────────── */}
      <Route path="/nw/monitoring/demo/digital-twin" element={wrap(<NwDigitalTwinDemoPage />)} />
      <Route path="/nw/monitoring/demo/topology"     element={wrap(<NwTopologyDemoPage />)} />

      <Route path="/home" element={<Navigate to={DEFAULT_PATH} replace />} />
    </Routes>
  );
}

export default AppRouter;
