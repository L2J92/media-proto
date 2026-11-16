import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, errorMessage, type ChargeResult, type Wallet } from '../lib/api'
import { useWallet } from '../lib/wallet'
import { points } from '../lib/points'
import { requestCardPayment } from '../lib/toss'

const PRESETS = [5000, 10000, 30000, 50000]
const TYPE_LABEL = { CHARGE: '충전', DONATION_SENT: '후원', DONATION_RECEIVED: '받은 후원' } as const

export function WalletPage() {
  const ctx = useWallet()
  const [wallet, setWallet] = useState<Wallet | null>(null)
  const [amount, setAmount] = useState(10000)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { api.wallet().then(setWallet).catch(e => setError(errorMessage(e))) }, [])

  async function charge(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const order = await api.prepareCharge(amount)
      await requestCardPayment(order)
    } catch (err) {
      const msg = errorMessage(err)
      setError(msg.includes('취소') ? '' : msg)
    } finally { setBusy(false) }
  }

  return (
    <div className="auth wallet-page">
      <h1>내 지갑</h1>
      <div className="wallet-balance">{points(wallet?.balance ?? ctx.balance ?? 0)}</div>

      <form className="form" onSubmit={charge}>
        <div className="presets">
          {PRESETS.map(p => (
            <button type="button" key={p} className={`btn btn-sm ${amount === p ? 'btn-primary' : ''}`} onClick={() => setAmount(p)}>
              +{points(p)}
            </button>
          ))}
          <input type="number" min={1000} max={1000000} step={1000} value={amount} onChange={e => setAmount(Number(e.target.value))} aria-label="충전 금액" />
        </div>
        {error && <p className="error small">{error}</p>}
        <button className="btn btn-primary" disabled={busy || amount < 1000}>
          {busy ? '결제창 여는 중…' : `${amount.toLocaleString('ko-KR')}원 결제하고 충전`}
        </button>
        <p className="muted small">토스페이먼츠 <b>테스트 결제</b>입니다. 실제 돈은 나가지 않습니다. (1P = 1원)</p>
      </form>

      <h3>포인트 내역</h3>
      {wallet && wallet.transactions.length === 0 && <p className="muted small">아직 내역이 없어요.</p>}
      <ul className="tx-list">
        {wallet?.transactions.map(t => (
          <li key={t.id}>
            <span className={`tx-type tx-${t.type.toLowerCase()}`}>{TYPE_LABEL[t.type]}</span>
            <span className="tx-desc">{t.description}</span>
            <span className={t.amount > 0 ? 'tx-plus' : 'tx-minus'}>{t.amount > 0 ? '+' : ''}{points(t.amount)}</span>
            <span className="muted small">{new Date(t.createdAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 결제창에서 돌아온 뒤 서버에 승인을 요청한다. 서버가 주문 금액·후원 대상을 다시 검증한다. */
export function ChargeSuccessPage() {
  const [params] = useSearchParams()
  const { setBalance } = useWallet()
  const [result, setResult] = useState<ChargeResult | null>(null)
  const [error, setError] = useState('')
  const sent = useRef(false)
  const broadcastId = params.get('broadcastId')
  const paymentKey = params.get('paymentKey')
  const orderId = params.get('orderId')
  const amount = Number(params.get('amount'))
  const invalid = !paymentKey || !orderId || !amount

  useEffect(() => {
    if (invalid || sent.current) return // StrictMode 이중 호출 방지 (서버도 멱등 처리)
    sent.current = true
    api.confirmCharge(paymentKey, orderId, amount)
      .then(r => { setResult(r); setBalance(r.balance) })
      .catch(e => setError(errorMessage(e)))
  }, [invalid, paymentKey, orderId, amount, setBalance])

  return (
    <div className="auth">
      {invalid && <p className="error">결제 정보가 올바르지 않습니다.</p>}
      {!invalid && !result && !error && <p className="muted"><span className="spinner">결제를 확인하고 있어요…</span></p>}
      {result && (
        result.donation ? (
          <>
            <h1>🎉 후원 완료</h1>
            <p>{points(result.chargedAmount)}를 충전해 <b>{result.donation.streamerNickname}</b>님에게 <b>{points(result.donation.amount)}</b>를 후원했어요.</p>
          </>
        ) : (
          <>
            <h1>💎 충전 완료</h1>
            <p><b>{points(result.chargedAmount)}</b>가 충전됐어요.</p>
            {broadcastId && <p className="muted small">방송이 삭제되어 후원은 진행되지 않았고, 포인트는 지갑에 남아 있어요.</p>}
          </>
        )
      )}
      {result && <p className="muted">현재 잔액 <b>{points(result.balance)}</b></p>}
      {error && (<><h1>결제에 실패했어요</h1><p className="error">{error}</p></>)}
      <div className="studio-controls">
        {broadcastId && <Link className="btn btn-primary" to={`/broadcasts/${broadcastId}`}>방송으로 돌아가기</Link>}
        <Link className="btn" to="/wallet">내 지갑</Link>
      </div>
    </div>
  )
}

export function ChargeFailPage() {
  const [params] = useSearchParams()
  const broadcastId = params.get('broadcastId')
  return (
    <div className="auth">
      <h1>결제가 완료되지 않았어요</h1>
      <p className="muted">{params.get('message') ?? '결제가 취소되었습니다.'}</p>
      <div className="studio-controls">
        {broadcastId && <Link className="btn btn-primary" to={`/broadcasts/${broadcastId}`}>방송으로 돌아가기</Link>}
        <Link className="btn" to="/wallet">내 지갑</Link>
      </div>
    </div>
  )
}
