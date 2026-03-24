import { createContext, useState, type ReactNode } from 'react';
import { getInitialTheme } from './themeUtils';

export type Theme = 'hud' | 'dark' | 'light';

const THEME_CYCLE: Theme[] = ['hud', 'dark', 'light'];

export interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(getInitialTheme);

  function toggleTheme() {
    setTheme((prev) => {
      const next = THEME_CYCLE[(THEME_CYCLE.indexOf(prev) + 1) % THEME_CYCLE.length];
      localStorage.setItem('theme', next);
      return next;
    });
  }

  // 'light' 는 :root 가 기본이므로 클래스 없음, 나머지는 테마명 그대로 클래스 적용
  const themeClass = theme === 'light' ? '' : theme;

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <div className={`${themeClass} min-h-screen bg-background text-foreground transition-colors duration-300`}>
        {children}
      </div>
    </ThemeContext.Provider>
  );
}
