import { useAuth } from '@/context/AuthContext';

export default function HomePage() {
  const { user } = useAuth();

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-foreground mb-1">
        안녕하세요, {user?.userName}님
      </h1>
      <p className="text-sm text-muted-foreground">Weekly Marking 대시보드입니다.</p>
    </div>
  );
}
