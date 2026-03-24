import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  BarChart2,
  Boxes,
  LogOut,
  Monitor,
  Network,
  Settings,
  TrendingUp,
  Users,
  Layers,
  Activity,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/AuthContext';
import { logout } from '@/api/auth/authApi';
import { cn } from '@/lib/utils';
import NavTree from './NavTree';
import type { MenuItem, NavItem } from '@/types';
import type { ComponentType } from 'react';

const ICON_MAP: Record<string, ComponentType<{ className?: string }>> = {
  'icon-nw':       Network,
  'icon-app':      Layers,
  'icon-monitor':  Monitor,
  'icon-analysis': BarChart2,
  'icon-kpi':      TrendingUp,
  'icon-cause':    AlertCircle,
  'icon-manage':   Settings,
  'icon-admin':    Settings,
  'icon-users':    Users,
  'icon-twin':     Boxes,
};

function menuToNavItems(items: MenuItem[]): NavItem[] {
  return items.map((item) => ({
    label: item.menuName,
    icon: item.icon ? ICON_MAP[item.icon] : undefined,
    path: item.menuPath ?? undefined,
    children: item.children.length > 0 ? menuToNavItems(item.children) : undefined,
  }));
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export default function Sidebar({ open, onClose }: SidebarProps) {
  const { user, menu, logoutUser } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation('common');

  async function handleLogout() {
    try {
      await logout();
    } finally {
      logoutUser();
      navigate('/login', { replace: true });
    }
  }

  return (
    <>
      {/* 백드롭 */}
      {open && (
        <div
          className="fixed inset-0 z-20 bg-black/30"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      {/* 사이드바 패널 */}
      <aside
        className={cn(
          'fixed top-14 left-0 z-20 flex flex-col w-64 h-[calc(100vh-3.5rem)]',
          'border-r border-border bg-card shadow-lg',
          'transition-transform duration-200',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* 시스템 브랜딩 */}
        <div className="flex items-center gap-2.5 px-4 py-3 border-b border-border shrink-0">
          <Activity className="size-4 text-primary shrink-0" />
          <span className="text-xs font-semibold tracking-widest text-foreground uppercase">
            NTTPOC
          </span>
        </div>

        {/* 트리 메뉴 */}
        <div className="flex-1 overflow-y-auto py-3 px-2">
          <NavTree items={menuToNavItems(menu)} onNavigate={onClose} />
        </div>

        {/* 사용자 정보 + 로그아웃 */}
        <div className="border-t border-border p-3 shrink-0">
          <div className="mb-2 px-3 py-1">
            <p className="text-xs font-medium text-foreground truncate">{user?.userName}</p>
            <p className="text-xs text-muted-foreground truncate">{user?.userId}</p>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 w-full px-3 py-2 text-sm rounded-lg text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
          >
            <LogOut className="size-4 shrink-0" />
            <span>{t('nav.logout')}</span>
          </button>
        </div>
      </aside>
    </>
  );
}
