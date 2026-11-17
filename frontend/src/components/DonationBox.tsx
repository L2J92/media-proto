import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, errorMessage, type Broadcast, type Donation, type DonationSummary } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useWallet } from '../lib/wallet'
import { newRequestId, points } from '../lib/points'
import { requestCardPayment } from '../lib/toss'
import { broadcastEvents, useServerEvent } from '../lib/realtime'

const PRESETS = [1000, 5000, 10000, 50000]

/**
 * 후원: 포인트가 충분하면 즉시 후원, 부족하면 "충전하고 바로 후원"(토스 결제 1회로 충전 + 후원).
 */
export function DonationBox({ broadcast }: { broadcast: Broadcast }) {
  const { me } = useAuth()
  const wallet = useWallet()
  const [summary, setSummary] = useState<DonationSummary | null>(null)
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState(5000)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [flash, setFlash] = useState<Donation | null>(null)
  // 같은 후원 시도(더블 클릭·재시도)는 같은 requestId 로 보내 서버에서 한 번만 처리되게 한다
  const requestIdRef = useRef(newRequestId())

  useEffect(() => {
    api.donations(broadcast.id).then(setSummary).catch(() => {})
  }, [broadcast.id])

  const addToSummary = (d: Donation) => setSummary(prev => prev && !prev.recent.some(r => r.id === d.id)
    ? { totalAmount: prev.totalAmount + d.amount, count: prev.count + 1, recent: [d, ...prev.recent].slice(0, 20) }
    : prev)

  // 다른 시청자의 후원이 적립되면 실시간(SSE)으로 합계를 갱신하고 알림을 띄운다.
  useServerEvent<Donation>(broadcastEvents(broadcast.id), 'donation', d => {
    addToSummary(d)
    setFlash(d)
    setTimeout(() => setFlash(cur => (cur?.id === d.id ? null : cur)), 6000)
  })

  const balance = wallet.balance ?? 0
  const enough = balance >= amount

  async function donateWithPoints() {
    setBusy(true); setError(''); setDone('')
    try {
      const res = await api.donate(broadcast.id, amount, message, requestIdRef.current)
      wallet.setBalance(res.balance)
      addToSummary(res.donation)
      setDone(`${points(res.donation.amount)} 후원했어요!`)
      setMessage('')
      requestIdRef.current = newRequestId() // 다음 후원은 새 요청
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INSUFFICIENT_POINTS') wallet.refresh()
      setError(errorMessage(err))
    } finally { setBusy(false) }
  }

  async function chargeAndDonate() {
    setBusy(true); setError(''); setDone('')
    try {
      const order = await api.prepareCharge(amount, { broadcastId: broadcast.id, message })
      await requestCardPayment(order, broadcast.id) // 결제 후 /wallet/charge/success 로 돌아온다
    } catch (err) {
      const msg = errorMessage(err)
      setError(msg.includes('취소') ? '' : msg)
    } finally { setBusy(false) }
  }

  const isOwner = me?.id === broadcast.owner.id
  return (
    <div className="donation">
      {flash && (
        <div className="donation-flash" role="status">
          💰 <b>{flash.donorNickname}</b>님이 <b>{points(flash.amount)}</b> 후원!{flash.message && <> “{flash.message}”</>}
        </div>
      )}
      <div className="donation-head">
        <div>
          <span className="muted small">누적 후원</span>{' '}
          <b className="donation-total">{points(summary?.totalAmount ?? 0)}</b>
          <span className="muted small"> · {summary?.count ?? 0}건</span>
        </div>
        {!isOwner && (
          me
            ? <button className="btn btn-primary btn-sm" onClick={() => { setOpen(o => !o); setDone('') }}>💰 후원하기</button>
            : <Link className="btn btn-sm" to={`/login?next=/broadcasts/${broadcast.id}`}>로그인 후 후원</Link>
        )}
      </div>

      {open && !isOwner && me && (
        <form className="donation-form" onSubmit={e => { e.preventDefault(); if (enough) donateWithPoints(); else chargeAndDonate() }}>
          <div className="wallet-line">
            내 포인트 <b>{points(balance)}</b> <Link className="small" to="/wallet">충전·내역 →</Link>
          </div>
          <div className="presets">
            {PRESETS.map(p => (
              <button type="button" key={p} className={`btn btn-sm ${amount === p ? 'btn-primary' : ''}`} onClick={() => setAmount(p)}>
                {points(p)}
              </button>
            ))}
            <input type="number" min={1000} max={1000000} step={1000} value={amount}
              onChange={e => setAmount(Number(e.target.value))} aria-label="직접 입력" />
          </div>
          <input placeholder="응원 메시지 (선택, 100자)" maxLength={100} value={message} onChange={e => setMessage(e.target.value)} />
          {error && <p className="error small">{error}</p>}
          {done && <p className="success small">🎉 {done}</p>}
          {enough ? (
            <button className="btn btn-primary" disabled={busy || amount < 1000}>
              {busy ? '후원 중…' : `${points(amount)} 후원하기`}
            </button>
          ) : (
            <>
              <button className="btn btn-primary" disabled={busy || amount < 1000}>
                {busy ? '결제창 여는 중…' : `${amount.toLocaleString('ko-KR')}원 충전하고 바로 후원`}
              </button>
              <p className="muted small">포인트가 부족해요. 토스 결제 한 번으로 {points(amount)} 충전과 후원이 함께 처리됩니다.</p>
            </>
          )}
          <p className="muted small">토스페이먼츠 <b>테스트 결제</b>로 실제 돈은 나가지 않습니다. (1P = 1원)</p>
        </form>
      )}

      {summary && summary.recent.length > 0 && (
        <ul className="donation-list">
          {summary.recent.slice(0, 5).map(d => (
            <li key={d.id}><b>{d.donorNickname}</b> {points(d.amount)}{d.message && <span className="muted"> · {d.message}</span>}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
