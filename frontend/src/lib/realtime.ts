import { useEffect, useRef } from 'react'

/**
 * 서버 실시간 이벤트(SSE) 구독. 같은 URL 은 EventSource 하나를 여러 컴포넌트가 나눠 쓴다(참조 카운트).
 * 끊기면 브라우저가 자동으로 다시 연결한다.
 */
interface Shared { es: EventSource; refs: number }
const sources = new Map<string, Shared>()

function acquire(url: string): EventSource {
  let s = sources.get(url)
  if (!s) {
    s = { es: new EventSource(url, { withCredentials: true }), refs: 0 }
    sources.set(url, s)
  }
  s.refs++
  return s.es
}

function release(url: string) {
  const s = sources.get(url)
  if (!s) return
  if (--s.refs <= 0) {
    s.es.close()
    sources.delete(url)
  }
}

/** url 의 event 이벤트가 오면 handler(JSON 파싱 결과)를 부른다. url 이 null 이면 구독하지 않는다. */
export function useServerEvent<T>(url: string | null, event: string, handler: (data: T) => void) {
  const ref = useRef(handler)
  useEffect(() => { ref.current = handler })

  useEffect(() => {
    if (!url) return
    const es = acquire(url)
    const listener = (e: MessageEvent) => {
      try { ref.current(JSON.parse(e.data) as T) } catch { /* 잘못된 메시지는 무시 */ }
    }
    es.addEventListener(event, listener as EventListener)
    return () => {
      es.removeEventListener(event, listener as EventListener)
      release(url)
    }
  }, [url, event])
}

export const broadcastEvents = (broadcastId: number) => `/api/realtime/broadcasts/${broadcastId}`
