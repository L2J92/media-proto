export const points = (n: number) => `${n.toLocaleString('ko-KR')}P`

export function newRequestId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
