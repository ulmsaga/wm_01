import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import ko_common from './locales/ko/common.json';
import ko_auth   from './locales/ko/auth.json';
import ko_nw     from './locales/ko/nw.json';
import en_common from './locales/en/common.json';
import en_auth   from './locales/en/auth.json';
import en_nw     from './locales/en/nw.json';
import ja_common from './locales/ja/common.json';
import ja_auth   from './locales/ja/auth.json';
import ja_nw     from './locales/ja/nw.json';

i18n
  .use(LanguageDetector)      // localStorage 'i18nextLng' → navigator.language 순서로 감지
  .use(initReactI18next)
  .init({
    resources: {
      ko: { common: ko_common, auth: ko_auth, nw: ko_nw },
      en: { common: en_common, auth: en_auth, nw: en_nw },
      ja: { common: ja_common, auth: ja_auth, nw: ja_nw },
    },
    defaultNS: 'common',
    fallbackLng: 'ja',        // localStorage 미설정 시 기본값: 일본어
    supportedLngs: ['ja', 'en', 'ko'],
    interpolation: {
      escapeValue: false,     // React 는 XSS 를 자체 처리
    },
    detection: {
      order: ['localStorage'], // navigator 제외 — localStorage 없으면 fallbackLng(ja) 사용
      lookupLocalStorage: 'lang',
      cacheUserLanguage: true,
    },
  });

export default i18n;
