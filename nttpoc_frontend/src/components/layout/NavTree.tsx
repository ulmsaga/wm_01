import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { NavItem } from '@/types';

interface NavTreeItemProps {
  item: NavItem;
  level: number;
  onNavigate: () => void;
}

/**
 * 재귀 트리 메뉴 아이템
 * level: 현재 깊이 (0부터)
 */
function NavTreeItem({ item, level, onNavigate }: NavTreeItemProps) {
  const location = useLocation();
  const hasChildren = (item.children?.length ?? 0) > 0;

  // 현재 경로가 하위 항목에 포함되면 기본으로 열어둠
  const isChildActive = hasChildren && item.children!.some((c) => isDescendantActive(c, location.pathname));
  const [open, setOpen] = useState(isChildActive);

  const Icon = item.icon;
  const indent = level * 12;

  if (hasChildren) {
    return (
      <div>
        <button
          onClick={() => setOpen((v) => !v)}
          style={{ paddingLeft: `${12 + indent}px` }}
          className={cn(
            'flex items-center gap-2 w-full pr-3 py-2 text-sm rounded-lg transition-colors',
            isChildActive
              ? 'text-primary font-medium'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground'
          )}
        >
          {Icon && <Icon className="size-4 shrink-0" />}
          <span className="flex-1 text-left truncate">{item.label}</span>
          <ChevronDown
            className={cn('size-3.5 shrink-0 transition-transform', open && 'rotate-180')}
          />
        </button>

        {open && (
          <div>
            {item.children!.map((child) => (
              <NavTreeItem key={child.label} item={child} level={level + 1} onNavigate={onNavigate} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <NavLink
      to={item.path!}
      onClick={onNavigate}
      style={{ paddingLeft: `${12 + indent}px` }}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-2 pr-3 py-2 text-sm rounded-lg transition-colors',
          isActive
            ? 'bg-primary/10 text-primary font-medium'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground'
        )
      }
    >
      {Icon && <Icon className="size-4 shrink-0" />}
      <span className="truncate">{item.label}</span>
    </NavLink>
  );
}

/** 재귀적으로 하위에 활성 경로가 있는지 확인 */
function isDescendantActive(item: NavItem, pathname: string): boolean {
  if (item.path && pathname.startsWith(item.path)) return true;
  return item.children?.some((c) => isDescendantActive(c, pathname)) ?? false;
}

interface NavTreeProps {
  items: NavItem[];
  onNavigate: () => void;
}

export default function NavTree({ items, onNavigate }: NavTreeProps) {
  return (
    <nav className="space-y-0.5">
      {items.map((item) => (
        <NavTreeItem key={item.label} item={item} level={0} onNavigate={onNavigate} />
      ))}
    </nav>
  );
}
