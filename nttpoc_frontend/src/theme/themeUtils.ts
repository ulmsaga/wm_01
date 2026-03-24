import type { Theme } from './ThemeContext';

export function getInitialTheme(): Theme {
  const saved = localStorage.getItem('theme') as Theme | null;
  if (saved === 'hud' || saved === 'dark' || saved === 'light') return saved;
  return 'hud'; // 기본값
}
