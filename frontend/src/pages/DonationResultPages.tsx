import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, errorMessage, type Donation } from '../lib/api'

/** 결제창에서 돌아온 뒤 서버에 승인을 요청한다. 서버가 금액·주문을 다시 검증한다. */
export function DonationSuccessPage() {
  const [params] = useSearchParams()
  const [result, setResult] = useState<Donation | null>(null)
  const [error, setError] = useState('')
  const sent = useRef(false)
  const broadcastId = params.get('broadcastId')
  const paymentKey = params.get('paymentKey')
  const orderId = params.get('orderId')
  const amount = Number(params.get('amount'))
  const invalid = !paymentKey || !orderId || !amount

  useEffect(() => {
    if (invalid || sent.current) return // StrictMode 에서 두 번 호출 방지 (서버도 멱등 처리)
    sent.current = true
    api.confirmDonation(paymentKey, orderId, amount).then(setResult).catch(e => setError(errorMessage(e)))
  }, [invalid, paymentKey, orderId, amount])

  return (
    <div className="auth">
      {invalid && <p className="error">결제 정보가 올바르지 않습니다.</p>}
      {!invalid && !result && !error && <p className="muted"><span className="spinner">결제를 확인하고 있어요…</span></p>}
      {result && (
        <>
          <h1>🎉 후원 완료</h1>
          <p><b>{result.streamerNickname}</b>님에게 <b>{result.amount.toLocaleString('ko-KR')}원</b>을 후원했어요.</p>
        </>
      )}
      {error && (
        <>
          <h1>후원에 실패했어요</h1>
          <p className="error">{error}</p>
        </>
      )}
      {broadcastId && <Link className="btn btn-primary" to={`/broadcasts/${broadcastId}`}>방송으로 돌아가기</Link>}
    </div>
  )
}

export function DonationFailPage() {
  const [params] = useSearchParams()
  const broadcastId = params.get('broadcastId')
  return (
    <div className="auth">
      <h1>결제가 완료되지 않았어요</h1>
      <p className="muted">{params.get('message') ?? '결제가 취소되었습니다.'}</p>
      {broadcastId && <Link className="btn btn-primary" to={`/broadcasts/${broadcastId}`}>방송으로 돌아가기</Link>}
    </div>
  )
}
