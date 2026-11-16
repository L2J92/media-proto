// 토스페이먼츠 결제창 SDK v2 를 필요할 때만 불러온다.
interface TossPayment {
  requestPayment(options: {
    method: 'CARD'
    amount: { currency: 'KRW'; value: number }
    orderId: string
    orderName: string
    successUrl: string
    failUrl: string
    customerName?: string
  }): Promise<void>
}
interface TossPaymentsInstance { payment(options: { customerKey: string }): TossPayment }
declare global {
  interface Window { TossPayments?: (clientKey: string) => TossPaymentsInstance }
}

let loading: Promise<void> | null = null

export function loadTossPayments(): Promise<void> {
  if (window.TossPayments) return Promise.resolve()
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = 'https://js.tosspayments.com/v2/standard'
      s.onload = () => resolve()
      s.onerror = () => { loading = null; reject(new Error('결제 모듈을 불러오지 못했습니다.')) }
      document.head.appendChild(s)
    })
  }
  return loading
}

export async function requestCardPayment(order: {
  clientKey: string; customerKey: string; orderId: string; orderName: string; amount: number; customerName: string
}, returnBroadcastId: number) {
  await loadTossPayments()
  const toss = window.TossPayments!(order.clientKey)
  const payment = toss.payment({ customerKey: order.customerKey })
  const origin = window.location.origin
  await payment.requestPayment({
    method: 'CARD',
    amount: { currency: 'KRW', value: order.amount },
    orderId: order.orderId,
    orderName: order.orderName,
    customerName: order.customerName,
    successUrl: `${origin}/donations/success?broadcastId=${returnBroadcastId}`,
    failUrl: `${origin}/donations/fail?broadcastId=${returnBroadcastId}`,
  })
}
