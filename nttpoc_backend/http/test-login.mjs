/**
 * 로그인 i18n 테스트 스크립트
 *
 * 실행: node wm_backend/http/test-login.mjs
 * 요구: Node.js 18+  (fetch, webcrypto 내장 — 추가 패키지 없음)
 */

import { webcrypto } from 'crypto';
const { subtle } = webcrypto;

// ── 테스트 설정 ────────────────────────────────────────────────────────
const BASE_URL  = 'http://localhost:8080/api';
const LOGIN_ID  = 'PTN1889130';          // ← 실제 계정으로 변경
const PASSWORD  = '';   // ← 실제 비밀번호로 변경
const LANG      = 'ko';             // 'ko' | 'en' | 'ja'
const FORCE     = false;            // 중복 로그인 강제 여부
// ──────────────────────────────────────────────────────────────────────

async function getRsaPublicKey() {
    const res = await fetch(`${BASE_URL}/auth/rsa-public-key`);
    const json = await res.json();
    if (!json.data?.keyId) throw new Error('공개키 발급 실패: ' + JSON.stringify(json));
    return json.data; // { keyId, publicKey(Base64) }
}

async function encryptCredentials(publicKeyBase64, loginId, password) {
    const keyBuf = Buffer.from(publicKeyBase64, 'base64');
    const cryptoKey = await subtle.importKey(
        'spki',
        keyBuf,
        { name: 'RSA-OAEP', hash: 'SHA-256' },
        false,
        ['encrypt']
    );
    const plaintext = JSON.stringify({ loginId, password });
    const encrypted = await subtle.encrypt(
        { name: 'RSA-OAEP' },
        cryptoKey,
        Buffer.from(plaintext)
    );
    return Buffer.from(encrypted).toString('base64');
}

async function login(keyId, encryptedCredentials, lang, forceLogin) {
    const res = await fetch(`${BASE_URL}/auth/login`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept-Language': lang,
        },
        body: JSON.stringify({ keyId, encryptedCredentials, forceLogin }),
    });
    const body = await res.json();
    return { status: res.status, headers: res.headers, body };
}

// ── 메인 ──────────────────────────────────────────────────────────────
async function main() {
    console.log(`\n${'─'.repeat(55)}`);
    console.log(` 로그인 테스트  |  계정: ${LOGIN_ID}  |  언어: ${LANG}`);
    console.log(`${'─'.repeat(55)}`);

    // 1. RSA 공개키 발급
    process.stdout.write('① RSA 공개키 발급 ... ');
    const { keyId, publicKey } = await getRsaPublicKey();
    console.log(`OK  (keyId: ${keyId.slice(0, 8)}...)`);

    // 2. 암호화
    process.stdout.write('② 인증정보 RSA 암호화 ... ');
    const encryptedCredentials = await encryptCredentials(publicKey, LOGIN_ID, PASSWORD);
    console.log('OK');

    // 3. 로그인 요청
    process.stdout.write('③ POST /auth/login ... ');
    const { status, headers, body } = await login(keyId, encryptedCredentials, LANG, FORCE);
    console.log(`HTTP ${status}`);

    // 4. 결과 출력
    console.log('\n[응답 본문]');
    console.log(JSON.stringify(body, null, 2));

    if (status === 200 && body.data && !body.data.requireDuplicateConfirm && !body.data.requireSecondAuth) {
        const setCookies = headers.getSetCookie?.() ?? [];
        console.log('\n[Set-Cookie]');
        setCookies.forEach(c => console.log(' ', c.split(';')[0])); // 값만 축약 출력
        console.log('\n✅ 로그인 성공');
    } else if (body.data?.requireSecondAuth) {
        console.log('\n⏳ 2차 인증(OTP) 필요 → otpSeq:', body.data.otpSeq);
    } else if (body.data?.requireDuplicateConfirm) {
        console.log('\n⚠️  중복 로그인 확인 필요 → FORCE=true 로 재실행하세요');
    } else {
        console.log('\n❌ 로그인 실패');
    }

    console.log(`${'─'.repeat(55)}\n`);
}

main().catch(err => {
    console.error('\n🔥 오류:', err.message);
    process.exit(1);
});
