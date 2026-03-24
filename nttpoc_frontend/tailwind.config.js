/** @type {import('tailwindcss').Config} */
export default {
  // Tailwind v4: 다크 모드는 index.css의 @custom-variant dark 로 제어
  // tailwind.config.js 의 darkMode 옵션은 v4에서 무시됨
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
  ],
  plugins: [],
};
