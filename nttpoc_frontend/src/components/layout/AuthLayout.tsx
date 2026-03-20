import type { ReactNode } from 'react';

function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen w-full bg-muted dark:bg-slate-950 transition-colors">
      {children}
    </div>
  );
}

export default AuthLayout;
