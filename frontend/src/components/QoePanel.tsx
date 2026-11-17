import { useEffect, useState } from 'react'
import { api, type QoeSummary } from '../lib/api'

/** 방송자용 시청 품질 대시보드 (최근 5분, 10초마다 갱신) */
export function QoePanel({ broadcastId }: { broadcastId: number }) {
  const [q, setQ] = useState<QoeSummary | null>(null)

  useEffect(() => {
    let alive = true
    const load = () => api.qoe(broadcastId, 5).then(r => alive && setQ(r)).catch(() => {})
    load()
    const t = setInterval(load, 10_000)
    return () => { alive = false; clearInterval(t) }
  }, [broadcastId])

  if (!q) return null
  const level = q.samples === 0 ? 'idle' : q.avgPacketLossPct > 3 || q.p95RttMs > 400 || q.totalFreezes > 5 ? 'bad'
    : q.avgPacketLossPct > 1 || q.p95RttMs > 200 ? 'warn' : 'good'
  const levelText = { idle: '시청자 품질 데이터 수집 중…', good: '시청 품질 양호', warn: '일부 시청자 품질 저하', bad: '시청 품질 나쁨' }[level]

  return (
    <section className="qoe">
      <div className="qoe-head">
        <b>시청 품질</b> <span className={`qoe-level qoe-${level}`}>● {levelText}</span>
        <span className="muted small">최근 {q.windowMinutes}분 · 보고 시청자 {q.reportingViewers}명</span>
      </div>
      {q.samples > 0 && (
        <>
          <div className="qoe-tiles">
            <Tile label="평균 비트레이트" value={`${Math.round(q.avgBitrateKbps).toLocaleString()} kbps`} />
            <Tile label="RTT 평균 / p95" value={`${Math.round(q.avgRttMs)} / ${Math.round(q.p95RttMs)} ms`} />
            <Tile label="패킷 손실" value={`${q.avgPacketLossPct.toFixed(2)}%`} />
            <Tile label="지터" value={`${q.avgJitterMs.toFixed(1)} ms`} />
            <Tile label="평균 FPS" value={q.avgFps.toFixed(1)} />
            <Tile label="끊김 / 프레임 드롭" value={`${q.totalFreezes} / ${q.totalFramesDropped}`} />
            <Tile label="주 해상도" value={q.typicalHeight ? `${q.typicalHeight}p` : '-'} />
            <Tile label="화질 선택" value={Object.entries(q.qualityShare).map(([k, v]) => `${k} ${v}%`).join(' · ') || '-'} />
          </div>
          <Sparkline points={q.timeline.map(b => b.avgBitrateKbps)} label="분당 평균 비트레이트(kbps)" />
        </>
      )}
    </section>
  )
}

function Tile({ label, value }: { label: string; value: string }) {
  return <div className="qoe-tile"><span>{label}</span><b>{value}</b></div>
}

function Sparkline({ points, label }: { points: number[]; label: string }) {
  if (points.length < 2) return null
  const w = 300, h = 48, max = Math.max(...points, 1)
  const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${(i / (points.length - 1)) * w},${h - (p / max) * (h - 4) - 2}`).join(' ')
  return (
    <figure className="qoe-spark">
      <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" role="img" aria-label={label}>
        <path d={d} fill="none" stroke="currentColor" strokeWidth="2" vectorEffect="non-scaling-stroke" />
      </svg>
      <figcaption className="muted small">{label} · 최대 {Math.round(max).toLocaleString()}</figcaption>
    </figure>
  )
}
