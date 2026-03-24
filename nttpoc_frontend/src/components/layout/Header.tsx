import { Menu } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

interface HeaderProps {
  onToggleSidebar: () => void;
}

export default function Header({ onToggleSidebar }: HeaderProps) {
  const { user } = useAuth();

  return (
    <header className="fixed top-0 left-0 right-0 z-30 flex items-center h-14 px-4 border-b border-border bg-card">
      <button
        onClick={onToggleSidebar}
        className="flex items-center justify-center size-8 rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
        aria-label="메뉴 열기/닫기"
      >
        <Menu className="size-5" />
      </button>

      <span className="ml-3 font-semibold text-primary">NTT POC</span>

      <div className="ml-auto flex items-center gap-2">
        <span className="text-sm text-muted-foreground hidden sm:block">{user?.userId}</span>
        <div className="flex items-center justify-center size-8 rounded-full bg-primary/10 text-primary text-sm font-medium select-none">
          {user?.userName?.charAt(0) ?? '?'}
        </div>
      </div>
    </header>
  );
}
