import axiosInstance from '../axiosInstance';
import type { ApiResponse, User } from '@/types';

interface RsaPublicKeyData {
  keyId: string;
  publicKey: string;
}

interface LoginParams {
  loginId: string;
  loginPw: string;
  forceLogin?: boolean;
}

export interface LoginResponseData {
  requireDuplicateConfirm?: boolean;
  message?: string;
  requireSecondAuth?: boolean;
  otpSeq?: number;
  sendType?: string;
  sendTarget?: string;
  userSeq?: number;
  userId?: string;
  userName?: string;
}

/** RSA 공개키 요청 (일회용, TTL 5분) */
export async function getRsaPublicKey(): Promise<ApiResponse<RsaPublicKeyData>> {
  const response = await axiosInstance.get<ApiResponse<RsaPublicKeyData>>('/auth/rsa-public-key');
  return response.data;
}

/** Web Crypto API로 RSA-OAEP 암호화 → Base64 반환 */
export async function encryptWithRsa(publicKeyBase64: string, plainText: string): Promise<string> {
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
export async function getMe(): Promise<ApiResponse<User>> {
  const response = await axiosInstance.get<ApiResponse<User>>('/auth/me');
  return response.data;
}

/** 2차 인증: OTP 검증 */
export async function verifyOtp(otpSeq: number, otpCode: string): Promise<ApiResponse<User>> {
  const response = await axiosInstance.post<ApiResponse<User>>('/auth/verify-otp', { otpSeq, otpCode });
  return response.data;
}

/** 로그아웃 */
export async function logout(): Promise<ApiResponse<void>> {
  const response = await axiosInstance.post<ApiResponse<void>>('/auth/logout');
  return response.data;
}

/** 로그인: RSA 공개키 → credentials 암호화 → 1차 인증 */
export async function login(params: LoginParams): Promise<ApiResponse<LoginResponseData>> {
  const keyResponse = await getRsaPublicKey();
  const { keyId, publicKey } = keyResponse.data!;

  const credentials = JSON.stringify({
    loginId: params.loginId,
    password: params.loginPw,
  });
  const encryptedCredentials = await encryptWithRsa(publicKey, credentials);

  const response = await axiosInstance.post<ApiResponse<LoginResponseData>>('/auth/login', {
    keyId,
    encryptedCredentials,
    forceLogin: params.forceLogin ?? false,
  });
  return response.data;
}
