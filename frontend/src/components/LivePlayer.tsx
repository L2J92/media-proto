import { useEffect, useRef, useState } from 'react'
import {
  Room, RoomEvent, Track,
  type RemoteTrack, type RemoteTrackPublication,
} from 'livekit-client'
import { api, errorMessage } from '../lib/api'

interface Props {
  broadcastId: number
  onRoom?: (room: Room | null) => void
  onEnded?: () => void
}

/** 시청 전용 플레이어. 서버에서 받은 시청 토큰으로만 접속한다. */
export function LivePlayer({ broadcastId, onRoom, onEnded }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const audioBoxRef = useRef<HTMLDivElement>(null)
  const [state, setState] = useState<'connecting' | 'waiting' | 'playing' | 'ended' | 'error'>('connecting')
  const [error, setError] = useState('')
  const [audioBlocked, setAudioBlocked] = useState(false)
  const roomRef = useRef<Room | null>(null)
  // 콜백이 바뀌어도 재접속하지 않도록 ref 로 보관
  const cbRef = useRef({ onRoom, onEnded })
  useEffect(() => { cbRef.current = { onRoom, onEnded } })

  useEffect(() => {
    let cancelled = false
    const room = new Room({ adaptiveStream: true, dynacast: true })
    roomRef.current = room

    const pickVideo = () => {
      // 화면 공유 > 카메라 순으로 보여준다.
      const pubs = [...room.remoteParticipants.values()].flatMap(p => [...p.videoTrackPublications.values()])
      const pub = pubs.find(p => p.source === Track.Source.ScreenShare && p.track)
        ?? pubs.find(p => p.track)
      if (pub?.track && videoRef.current) {
        pub.track.attach(videoRef.current)
        setState('playing')
      } else {
        setState('waiting')
      }
    }

    room
      .on(RoomEvent.TrackSubscribed, (track: RemoteTrack) => {
        if (track.kind === Track.Kind.Audio) {
          const el = track.attach()
          audioBoxRef.current?.appendChild(el)
        } else {
          pickVideo()
        }
      })
      .on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack, _pub: RemoteTrackPublication) => {
        track.detach().forEach(el => { if (el.tagName === 'AUDIO') el.remove() })
        pickVideo()
      })
      .on(RoomEvent.ParticipantDisconnected, pickVideo)
      .on(RoomEvent.AudioPlaybackStatusChanged, () => setAudioBlocked(!room.canPlaybackAudio))
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
      roomRef.current = null
    }
  }, [broadcastId])

  return (
    <div className="player">
      <video ref={videoRef} autoPlay playsInline muted={false} controls={false} />
      <div ref={audioBoxRef} hidden />
      {state !== 'playing' && (
        <div className="player-overlay">
          {state === 'connecting' && '연결 중…'}
          {state === 'waiting' && '방송자의 화면을 기다리는 중…'}
          {state === 'ended' && '방송이 종료되었습니다.'}
          {state === 'error' && error}
        </div>
      )}
      {audioBlocked && state === 'playing' && (
        <button className="btn btn-primary unmute" onClick={() => roomRef.current?.startAudio()}>
          🔊 소리 켜기
        </button>
      )}
    </div>
  )
}
