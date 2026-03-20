import { createContext, useState, useEffect, type ReactNode } from 'react';
import { getInitialTheme } from './themeUtils';

export interface ThemeContextValue {
  theme: string;
  toggleTheme: () => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<string>(getInitialTheme);

  useEffect(() => {
    localStorage.setItem('theme', theme);
  }, [theme]);

  function toggleTheme() {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <div className={`${theme === 'dark' ? 'dark' : ''} min-h-screen bg-background text-foreground transition-colors`}>
        {children}
      </div>
    </ThemeContext.Provider>
  );
}
