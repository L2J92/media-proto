import { useEffect, useRef, useState } from 'react'
import {
  Room, RoomEvent, Track, VideoQuality,
  type RemoteAudioTrack, type RemoteTrack,
} from 'livekit-client'
import { api, errorMessage, type VideoQualityPref } from '../lib/api'
import { aggregate, ReceiverStats, type QoeSnapshot } from '../lib/qoe'

const QUALITY_LABEL: Record<VideoQualityPref, string> = { auto: '자동', high: '고화질', medium: '중간', low: '저화질' }
// 자동 = 가장 높은 레이어를 요청하고, 대역폭이 부족하면 SFU 가 낮은 레이어로 내려 보낸다.
const QUALITY_LAYER: Record<VideoQualityPref, VideoQuality> = {
  auto: VideoQuality.HIGH, high: VideoQuality.HIGH, medium: VideoQuality.MEDIUM, low: VideoQuality.LOW,
}
const STATS_INTERVAL_MS = 2000
const REPORT_EVERY = 5 // 2초 × 5 = 10초마다 서버에 품질 보고

interface Props {
  broadcastId: number
  onRoom?: (room: Room | null) => void
  onEnded?: () => void
}

type PlayerState = 'connecting' | 'waiting' | 'playing' | 'reconnecting' | 'ended' | 'error'

/** 시청 전용 플레이어. 서버에서 받은 시청 토큰으로만 접속한다. */
export function LivePlayer({ broadcastId, onRoom, onEnded }: Props) {
  const boxRef = useRef<HTMLDivElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const audioBoxRef = useRef<HTMLDivElement>(null)
  const roomRef = useRef<Room | null>(null)
  const audioTracksRef = useRef<Set<RemoteAudioTrack>>(new Set())
  const [state, setState] = useState<PlayerState>('connecting')
  const [error, setError] = useState('')
  const [audioBlocked, setAudioBlocked] = useState(false)
  const [viewers, setViewers] = useState(0)
  const [volume, setVolume] = useState(1)
  const [muted, setMuted] = useState(false)
  const [quality, setQuality] = useState<VideoQualityPref>('auto')
  const [menuOpen, setMenuOpen] = useState(false)
  const [showStats, setShowStats] = useState(false)
  const [stats, setStats] = useState<QoeSnapshot | null>(null)
  const qualityRef = useRef<VideoQualityPref>('auto')
  const videoTrackRef = useRef<RemoteTrack | null>(null)
  const cbRef = useRef({ onRoom, onEnded })
  useEffect(() => { cbRef.current = { onRoom, onEnded } })

  // 볼륨·음소거는 모든 오디오 트랙(화면 소리 + 마이크)에 적용
  useEffect(() => {
    audioTracksRef.current.forEach(t => t.setVolume(muted ? 0 : volume))
  }, [volume, muted])

  useEffect(() => {
    let cancelled = false
    // 화질을 직접 고를 수 있게 adaptiveStream 은 끈다 (레이어는 setVideoQuality 로 지정)
    const room = new Room({ adaptiveStream: false, dynacast: true })
    roomRef.current = room
    const audioTracks = audioTracksRef.current

    const countViewers = () => {
      // 나를 포함한 시청자 수 (송출자 제외)
      const remoteViewers = [...room.remoteParticipants.values()].filter(p => !p.identity.startsWith('publisher-')).length
      setViewers(remoteViewers + 1)
    }

    const pickVideo = () => {
      const pubs = [...room.remoteParticipants.values()].flatMap(p => [...p.videoTrackPublications.values()])
      const pub = pubs.find(p => p.source === Track.Source.ScreenShare && p.track) ?? pubs.find(p => p.track)
      if (pub?.track && videoRef.current) {
        pub.track.attach(videoRef.current)
        pub.setVideoQuality(QUALITY_LAYER[qualityRef.current])
        videoTrackRef.current = pub.track
        setState(s => (s === 'reconnecting' ? s : 'playing'))
      } else {
        setState(s => (s === 'reconnecting' ? s : 'waiting'))
      }
    }

    room
      .on(RoomEvent.TrackSubscribed, (track: RemoteTrack) => {
        if (track.kind === Track.Kind.Audio) {
          const audio = track as RemoteAudioTrack
          audioTracks.add(audio)
          audioBoxRef.current?.appendChild(audio.attach())
        } else {
          pickVideo()
        }
      })
      .on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack) => {
        if (track.kind === Track.Kind.Audio) audioTracks.delete(track as RemoteAudioTrack)
        track.detach().forEach(el => { if (el.tagName === 'AUDIO') el.remove() })
        pickVideo()
      })
      .on(RoomEvent.ParticipantConnected, countViewers)
      .on(RoomEvent.ParticipantDisconnected, () => { countViewers(); pickVideo() })
      .on(RoomEvent.AudioPlaybackStatusChanged, () => setAudioBlocked(!room.canPlaybackAudio))
      .on(RoomEvent.Reconnecting, () => setState('reconnecting'))
      .on(RoomEvent.Reconnected, () => { setState('connecting'); pickVideo() })
      .on(RoomEvent.Disconnected, () => {
        if (cancelled) return
        setState('ended')
        cbRef.current.onRoom?.(null)
        cbRef.current.onEnded?.()
      })

    ;(async () => {
      try {
        const conn = await api.viewerToken(broadcastId)
        if (cancelled) return
        await room.connect(conn.serverUrl, conn.token, { autoSubscribe: true })
        if (cancelled) { room.disconnect(); return }
        cbRef.current.onRoom?.(room)
        setAudioBlocked(!room.canPlaybackAudio)
        countViewers()
        pickVideo()
      } catch (e) {
        if (cancelled) return
        setError(errorMessage(e))
        setState('error')
      }
    })()

    return () => {
      cancelled = true
      cbRef.current.onRoom?.(null)
      room.disconnect()
      audioTracks.clear()
      roomRef.current = null
    }
  }, [broadcastId])

  function changeQuality(q: VideoQualityPref) {
    qualityRef.current = q
    setQuality(q)
    setMenuOpen(false)
    roomRef.current?.remoteParticipants.forEach(p =>
      p.videoTrackPublications.forEach(pub => pub.setVideoQuality(QUALITY_LAYER[q])))
  }

  // 수신 품질 측정: 2초마다 통계(오버레이), 10초마다 평균을 서버에 보고(방송자 품질 대시보드)
  const playing = state === 'playing'
  useEffect(() => {
    if (!playing) return
    let collector: ReceiverStats | null = null
    let collectorTrack: RemoteTrack | null = null
    const buffer: QoeSnapshot[] = []
    const t = setInterval(async () => {
      const track = videoTrackRef.current
      const receiver = track?.receiver
      if (!track || !receiver) return
      if (collectorTrack !== track) { collector = new ReceiverStats(receiver); collectorTrack = track; buffer.length = 0 }
      try {
        const snap = await collector!.sample()
        if (!snap) return
        setStats(snap)
        buffer.push(snap)
        if (buffer.length >= REPORT_EVERY) {
          const viewerKey = roomRef.current?.localParticipant.identity
          const sample = aggregate(buffer.splice(0))
          if (viewerKey) api.reportQoe(broadcastId, { ...sample, viewerKey, quality: qualityRef.current }).catch(() => {})
        }
      } catch { /* 통계 실패는 재생에 영향 없음 */ }
    }, STATS_INTERVAL_MS)
    return () => clearInterval(t)
  }, [playing, broadcastId])

  function toggleFullscreen() {
    const box = boxRef.current
    const video = videoRef.current as (HTMLVideoElement & { webkitEnterFullscreen?: () => void }) | null
    if (document.fullscreenElement) { document.exitFullscreen(); return }
    if (box?.requestFullscreen) box.requestFullscreen().catch(() => video?.webkitEnterFullscreen?.())
    else video?.webkitEnterFullscreen?.() // iOS Safari
  }

  const live = state === 'playing' || state === 'reconnecting'
  return (
    <div className="player" ref={boxRef}>
      <video ref={videoRef} autoPlay playsInline muted />
      <div ref={audioBoxRef} hidden />
      {live && <span className="onair">● LIVE</span>}
      {live && <span className="viewers">👁 {viewers.toLocaleString()}</span>}
      {state !== 'playing' && (
        <div className="player-overlay">
          {state === 'connecting' && <span className="spinner">연결 중…</span>}
          {state === 'waiting' && '방송자의 화면을 기다리는 중…'}
          {state === 'reconnecting' && <span className="spinner">네트워크가 불안정해 다시 연결하는 중…</span>}
          {state === 'ended' && '방송이 종료되었습니다.'}
          {state === 'error' && error}
        </div>
      )}
      {audioBlocked && live && (
        <button className="btn btn-primary unmute" onClick={() => roomRef.current?.startAudio()}>
          🔊 소리 켜기
        </button>
      )}
      {live && (
        <div className="player-controls">
          <button className="icon-btn" onClick={() => setMuted(m => !m)} aria-label={muted ? '음소거 해제' : '음소거'}>
            {muted || volume === 0 ? '🔇' : '🔊'}
          </button>
          <input type="range" min={0} max={1} step={0.05} value={muted ? 0 : volume}
            aria-label="볼륨" onChange={e => { setVolume(Number(e.target.value)); setMuted(false) }} />
          <span className="spacer" />
          <button className={`icon-btn ${showStats ? 'on' : ''}`} onClick={() => setShowStats(v => !v)} aria-label="통계" title="전문가용 통계">📊</button>
          <div className="quality-menu">
            <button className="icon-btn quality-btn" onClick={() => setMenuOpen(o => !o)} aria-label="화질">⚙ {QUALITY_LABEL[quality]}</button>
            {menuOpen && (
              <ul role="menu">
                {(Object.keys(QUALITY_LABEL) as VideoQualityPref[]).map(q => (
                  <li key={q}><button className={q === quality ? 'on' : ''} onClick={() => changeQuality(q)}>{QUALITY_LABEL[q]}</button></li>
                ))}
              </ul>
            )}
          </div>
          <button className="icon-btn" onClick={toggleFullscreen} aria-label="전체 화면">⛶</button>
        </div>
      )}
      {live && showStats && stats && (
        <div className="stats-overlay" role="status">
          <div><span>해상도</span>{stats.frameWidth}×{stats.frameHeight} @ {stats.fps.toFixed(0)}fps</div>
          <div><span>코덱</span>{stats.codec || '-'}</div>
          <div><span>비트레이트</span>{stats.bitrateKbps.toLocaleString()} kbps</div>
          <div><span>패킷 손실</span>{stats.packetLossPct.toFixed(2)}%</div>
          <div><span>지터</span>{stats.jitterMs.toFixed(1)} ms</div>
          <div><span>RTT</span>{stats.rttMs.toFixed(0)} ms</div>
          <div><span>프레임 드롭</span>{stats.framesDropped} · 끊김 {stats.freezeCount}</div>
          <div><span>화질</span>{QUALITY_LABEL[quality]}</div>
        </div>
      )}
    </div>
  )
}
