import { useNavigate } from 'react-router-dom';
import { LayoutDashboard, LogOut } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { logout } from '@/api/auth/authApi';
import { cn } from '@/lib/utils';
import NavTree from './NavTree';
import type { NavItem } from '@/types';

const NAV_ITEMS: NavItem[] = [
  {
    label: '대시보드',
    icon: LayoutDashboard,
    path: '/home',
  },
  // RBAC 구현 후 동적 메뉴로 교체 예정
];

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export default function Sidebar({ open, onClose }: SidebarProps) {
  const { user, logoutUser } = useAuth();
  const navigate = useNavigate();

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
        {/* 트리 메뉴 */}
        <div className="flex-1 overflow-y-auto py-3 px-2">
          <NavTree items={NAV_ITEMS} onNavigate={onClose} />
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
            <span>로그아웃</span>
          </button>
        </div>
      </aside>
    </>
  );
}
