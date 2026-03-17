import axiosInstance from '../axiosInstance';

/** RSA 공개키 요청 (일회용, TTL 5분) */
export async function getRsaPublicKey() {
  const response = await axiosInstance.get('/auth/rsa-public-key');
  return response.data;
}

/** Web Crypto API로 RSA-OAEP 암호화 → Base64 반환 */
export async function encryptWithRsa(publicKeyBase64, plainText) {
  const binaryDer = Uint8Array.from(atob(publicKeyBase64), (c) => c.charCodeAt(0));

  const cryptoKey = await window.crypto.subtle.importKey(
    'spki',
    binaryDer.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );

  const encoded = new TextEncoder().encode(plainText);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    cryptoKey,
    encoded
  );

  const bytes = new Uint8Array(encrypted);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/** 현재 인증된 사용자 정보 조회 (세션 복원용) */
export async function getMe() {
  const response = await axiosInstance.get('/auth/me');
  return response.data;
}

/** 2차 인증: OTP 검증 */
export async function verifyOtp(otpSeq, otpCode) {
  const response = await axiosInstance.post('/auth/verify-otp', { otpSeq, otpCode });
  return response.data;
}

/** 로그아웃 */
export async function logout() {
  const response = await axiosInstance.post('/auth/logout');
  return response.data;
}

/** 로그인: RSA 공개키 → credentials 암호화 → 1차 인증 */
export async function login(params) {
  // 1. RSA 공개키 가져오기
  const keyResponse = await getRsaPublicKey();
  const { keyId, publicKey } = keyResponse.data;

  // 2. loginId + password를 JSON으로 묶어 RSA 암호화
  const credentials = JSON.stringify({
    loginId: params.loginId,
    password: params.loginPw,
  });
  const encryptedCredentials = await encryptWithRsa(publicKey, credentials);

  // 3. 암호화된 데이터로 로그인 요청
  const response = await axiosInstance.post('/auth/login', {
    keyId,
    encryptedCredentials,
  });
  return response.data;
}