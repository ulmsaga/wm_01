import 'i18next';
import type ko_common from '@/i18n/locales/ko/common.json';
import type ko_auth   from '@/i18n/locales/ko/auth.json';
import type ko_nw     from '@/i18n/locales/ko/nw.json';

/**
 * i18next 키 타입 자동 추론.
 * 존재하지 않는 키를 t() 에 넘기면 TypeScript 컴파일 에러 발생.
 * 새 네임스페이스 추가 시 이 파일에도 import + resources 에 추가할 것.
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common';
    resources: {
      common: typeof ko_common;
      auth:   typeof ko_auth;
      nw:     typeof ko_nw;
    };
  }
}
