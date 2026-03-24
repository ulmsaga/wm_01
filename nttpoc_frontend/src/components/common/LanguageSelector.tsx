import { useTranslation } from 'react-i18next';

const LANGUAGES = [
  { code: 'ja', label: '日本語' },
  { code: 'en', label: 'English' },
  { code: 'ko', label: '한국어' },
] as const;

export default function LanguageSelector() {
  const { i18n } = useTranslation();

  return (
    <div className="flex items-center gap-1">
      {LANGUAGES.map(({ code, label }, idx) => (
        <span key={code} className="flex items-center">
          <button
            type="button"
            onClick={() => i18n.changeLanguage(code)}
            className={[
              'text-xs px-1.5 py-0.5 rounded transition-colors',
              i18n.language === code
                ? 'text-primary font-semibold'
                : 'text-muted-foreground hover:text-foreground',
            ].join(' ')}
          >
            {label}
          </button>
          {idx < LANGUAGES.length - 1 && (
            <span className="text-border text-xs select-none">|</span>
          )}
        </span>
      ))}
    </div>
  );
}
