import { useState, type ReactNode } from 'react';
import Header from './Header';
import Sidebar from './Sidebar';

export default function MainLayout({ children }: { children: ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-background">
      <Header onToggleSidebar={() => setSidebarOpen((v) => !v)} />
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <main className="pt-14 h-screen overflow-hidden">
        {children}
      </main>
    </div>
  );
}
