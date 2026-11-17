/**
 * WebRTC 수신 품질 측정. RTCRtpReceiver.getStats() 의 표준 통계(inbound-rtp, candidate-pair)만 사용한다.
 * sample() 을 부를 때마다 직전 호출 이후 구간의 값을 돌려준다.
 */
export interface QoeSnapshot {
  bitrateKbps: number
  packetLossPct: number
  jitterMs: number
  rttMs: number
  fps: number
  framesDropped: number
  freezeCount: number
  frameWidth: number
  frameHeight: number
  codec: string
}

interface Counters { t: number; bytes: number; lost: number; received: number; dropped: number; freezes: number }

export class ReceiverStats {
  private prev: Counters | null = null
  private readonly receiver: RTCRtpReceiver
  constructor(receiver: RTCRtpReceiver) { this.receiver = receiver }

  async sample(): Promise<QoeSnapshot | null> {
    const report = await this.receiver.getStats()
    let inbound: Record<string, number | string> | null = null
    let rtt = 0
    const codecs = new Map<string, string>()
    report.forEach((s: Record<string, unknown>) => {
      if (s.type === 'inbound-rtp' && s.kind === 'video') inbound = s as Record<string, number | string>
      if (s.type === 'candidate-pair' && (s.nominated || s.state === 'succeeded') && typeof s.currentRoundTripTime === 'number') {
        rtt = Math.max(rtt, s.currentRoundTripTime * 1000)
      }
      if (s.type === 'codec') codecs.set(String(s.id), String(s.mimeType ?? ''))
    })
    if (!inbound) return null
    const s = inbound as Record<string, number | string>
    const now: Counters = {
      t: Number(s.timestamp) || performance.now(),
      bytes: Number(s.bytesReceived) || 0,
      lost: Math.max(0, Number(s.packetsLost) || 0),
      received: Number(s.packetsReceived) || 0,
      dropped: Number(s.framesDropped) || 0,
      freezes: Number(s.freezeCount) || 0,
    }
    const p = this.prev
    this.prev = now
    if (!p) return null // 첫 호출은 기준점만 잡는다
    const dt = Math.max(0.001, (now.t - p.t) / 1000)
    const lost = now.lost - p.lost
    const recv = now.received - p.received
    return {
      bitrateKbps: Math.max(0, Math.round(((now.bytes - p.bytes) * 8) / dt / 1000)),
      packetLossPct: lost + recv > 0 ? Math.max(0, (lost / (lost + recv)) * 100) : 0,
      jitterMs: (Number(s.jitter) || 0) * 1000,
      rttMs: rtt,
      fps: Number(s.framesPerSecond) || 0,
      framesDropped: Math.max(0, now.dropped - p.dropped),
      freezeCount: Math.max(0, now.freezes - p.freezes),
      frameWidth: Number(s.frameWidth) || 0,
      frameHeight: Number(s.frameHeight) || 0,
      codec: (codecs.get(String(s.codecId)) ?? '').replace('video/', ''),
    }
  }
}

/** 여러 구간 측정값을 서버 보고용 한 건으로 합친다 (평균, 횟수는 합계) */
export function aggregate(list: QoeSnapshot[]) {
  const avg = (f: (x: QoeSnapshot) => number) => list.reduce((a, x) => a + f(x), 0) / list.length
  const sum = (f: (x: QoeSnapshot) => number) => list.reduce((a, x) => a + f(x), 0)
  const clamp = (v: number, max: number) => Math.min(max, Math.max(0, v))
  return {
    bitrateKbps: Math.round(clamp(avg(x => x.bitrateKbps), 100_000)),
    packetLossPct: Number(clamp(avg(x => x.packetLossPct), 100).toFixed(2)),
    jitterMs: Number(clamp(avg(x => x.jitterMs), 10_000).toFixed(1)),
    rttMs: Number(clamp(avg(x => x.rttMs), 10_000).toFixed(1)),
    fps: Number(clamp(avg(x => x.fps), 240).toFixed(1)),
    framesDropped: Math.round(clamp(sum(x => x.framesDropped), 100_000)),
    freezeCount: Math.round(clamp(sum(x => x.freezeCount), 10_000)),
    frameHeight: Math.round(clamp(list[list.length - 1].frameHeight, 4320)),
  }
}
