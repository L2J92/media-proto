import { useEffect, useRef, useState } from 'react'
import {
  Room, RoomEvent, Track,
  type RemoteAudioTrack, type RemoteTrack,
} from 'livekit-client'
import { api, errorMessage } from '../lib/api'

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
  const cbRef = useRef({ onRoom, onEnded })
  useEffect(() => { cbRef.current = { onRoom, onEnded } })

  // 볼륨·음소거는 모든 오디오 트랙(화면 소리 + 마이크)에 적용
  useEffect(() => {
    audioTracksRef.current.forEach(t => t.setVolume(muted ? 0 : volume))
  }, [volume, muted])

  useEffect(() => {
    let cancelled = false
    const room = new Room({ adaptiveStream: true, dynacast: true })
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
          <button className="icon-btn" onClick={toggleFullscreen} aria-label="전체 화면">⛶</button>
        </div>
      )}
    </div>
  )
}
