const SSE_URL = `${import.meta.env.VITE_API_BASE_URL}/sse/session`;

let eventSource = null;

/**
 * SSE 연결을 시작한다.
 *
 * @param {() => void} onInvalidated - SESSION_INVALIDATED 수신 시 콜백 (중복 로그인 로그아웃)
 * @param {() => void} [onError]     - EventSource가 4xx로 영구 종료 시 콜백 (세션 재확인용)
 *
 * onerror 동작:
 *  - readyState CONNECTING(0): 일시적 네트워크 오류 → 브라우저 자동 재연결 대기, 아무 조치 안 함
 *  - readyState CLOSED(2):     4xx/5xx 응답 → 자동 재연결 불가 → onError 호출하여 세션 재확인
 */
export function connectSse(onInvalidated, onError) {
  if (eventSource) disconnectSse();

  eventSource = new EventSource(SSE_URL, { withCredentials: true });

  eventSource.addEventListener('SESSION_INVALIDATED', () => {
    disconnectSse();
    onInvalidated();
  });

  eventSource.onerror = () => {
    if (eventSource && eventSource.readyState === EventSource.CLOSED) {
      disconnectSse();
      onError?.();
    }
  };
}

/** SSE 연결을 종료한다. */
export function disconnectSse() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}
