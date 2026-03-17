
import { createContext, useState, useEffect } from 'react';
import { getInitialTheme } from './themeUtils';


const ThemeContext = createContext();

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(getInitialTheme);

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

export { ThemeContext };
