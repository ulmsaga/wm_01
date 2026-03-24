import { useTranslation } from 'react-i18next';
import { useAuth } from '@/context/AuthContext';

export default function HomePage() {
  const { user } = useAuth();
  const { t } = useTranslation('common');

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold text-foreground mb-1">
        {t('home.greeting', { name: user?.userName })}
      </h1>
      <p className="text-sm text-muted-foreground">{t('nav.dashboard')}</p>
    </div>
  );
}
