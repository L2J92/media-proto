import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import type { Room } from 'livekit-client'
import { api, ApiError, errorMessage, type Broadcast, type BroadcastSource } from './api'

/**
 * 송출 세션을 화면(페이지) 밖, 앱 전체에 보관한다.
 * 송출 중에 다른 페이지로 이동해도 미디어 연결과 장치가 유지되고, 상단 바에서 상태를 볼 수 있다.
 */
export interface MediaTracks { video?: MediaStreamTrack; screenAudio?: MediaStreamTrack; mic?: MediaStreamTrack }

export interface LiveSession {
  broadcast: Broadcast
  source: BroadcastSource
  room: Room
  videoTrack: MediaStreamTrack | null
  viewers: number
  reconnecting: boolean
  micMuted: boolean
  hasMic: boolean
  hasSystemAudio: boolean
  facing: 'user' | 'environment'
}

interface EndedNotice { broadcastId: number; reason?: string; broadcast?: Broadcast }

interface Ctx {
  session: LiveSession | null
  ended: EndedNotice | null
  goLive: (broadcast: Broadcast, source: BroadcastSource, tracks: MediaTracks, facing: 'user' | 'environment', record?: boolean) => Promise<void>
  end: (reason?: string) => Promise<void>
  toggleMic: () => void
  switchCamera: () => Promise<void>
}

const SessionContext = createContext<Ctx | null>(null)

const cameraConstraints = (facing: 'user' | 'environment') =>
  ({ facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } })

export function BroadcastSessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<LiveSession | null>(null)
  const [ended, setEnded] = useState<EndedNotice | null>(null)
  const roomRef = useRef<Room | null>(null)
  const tracksRef = useRef<MediaTracks>({})
  const sessionRef = useRef<LiveSession | null>(null)
  const endingRef = useRef(false)
  // livekit-client 는 송출을 시작할 때만 불러온다(첫 화면 번들을 가볍게 유지)
  const lkRef = useRef<typeof import('livekit-client') | null>(null)

  const update = useCallback((patch: Partial<LiveSession>) => {
    setSession(prev => {
      const next = prev ? { ...prev, ...patch } : prev
      sessionRef.current = next
      return next
    })
  }, [])

  const cleanup = useCallback(async (notice: EndedNotice) => {
    const room = roomRef.current
    roomRef.current = null
    const t = tracksRef.current
    ;[t.video, t.screenAudio, t.mic].forEach(tr => tr?.stop())
    tracksRef.current = {}
    sessionRef.current = null
    setSession(null)
    setEnded(notice)
    await room?.disconnect()
  }, [])

  const end = useCallback(async (reason?: string) => {
    const s = sessionRef.current
    if (!s || endingRef.current) return
    endingRef.current = true
    let broadcast: Broadcast | undefined
    let message = reason
    try {
      broadcast = await api.end(s.broadcast.id)
    } catch (e) {
      // 이미 종료된 경우(서버 자동 종료 등)는 정상으로 본다
      if (!(e instanceof ApiError && e.code === 'BROADCAST_NOT_LIVE')) message = errorMessage(e)
    } finally {
      await cleanup({ broadcastId: s.broadcast.id, reason: message, broadcast })
      endingRef.current = false
    }
  }, [cleanup])

  const goLive = useCallback(async (broadcast: Broadcast, source: BroadcastSource, tracks: MediaTracks,
                                     facing: 'user' | 'environment', record = true) => {
    if (sessionRef.current) throw new Error('이미 다른 방송을 송출 중입니다.')
    setEnded(null)
    const lk = lkRef.current ?? (lkRef.current = await import('livekit-client'))
    const { Room, RoomEvent, Track, AudioPresets, VideoPresets } = lk
    const conn = broadcast.status === 'LIVE'
      ? await api.publisherToken(broadcast.id)   // 새로고침 후 재접속
      : await api.start(broadcast.id, source, record)
    const room = new Room({ adaptiveStream: false, dynacast: true, publishDefaults: { simulcast: true } })
    const countViewers = () => update({
      viewers: [...room.remoteParticipants.values()].filter(p => p.identity.startsWith('viewer-')).length,
    })
    room
      .on(RoomEvent.ParticipantConnected, countViewers)
      .on(RoomEvent.ParticipantDisconnected, countViewers)
      .on(RoomEvent.Reconnecting, () => update({ reconnecting: true }))
      .on(RoomEvent.Reconnected, () => update({ reconnecting: false }))
      .on(RoomEvent.Disconnected, () => {
        // 서버가 방을 정리한 경우(종료/자동 종료/삭제)
        if (roomRef.current === room && !endingRef.current) {
          cleanup({ broadcastId: broadcast.id, reason: '방송이 종료되었습니다.' })
        }
      })
    try {
      await room.connect(conn.serverUrl, conn.token)
      const { video, screenAudio, mic } = tracks
      if (video) {
        await room.localParticipant.publishTrack(video, source === 'SCREEN'
          ? { source: Track.Source.ScreenShare, videoEncoding: { maxBitrate: 3_000_000, maxFramerate: 30 }, simulcast: true }
          : { source: Track.Source.Camera, videoEncoding: VideoPresets.h720.encoding })
      }
      if (screenAudio) {
        await room.localParticipant.publishTrack(screenAudio, {
          source: Track.Source.ScreenShareAudio, audioPreset: AudioPresets.musicHighQualityStereo, dtx: false, red: false,
        })
      }
      if (mic) await room.localParticipant.publishTrack(mic, { source: Track.Source.Microphone })
    } catch (e) {
      await room.disconnect()
      throw e
    }

    roomRef.current = room
    tracksRef.current = tracks
    if (source === 'SCREEN' && tracks.video) {
      // 브라우저의 "공유 중지"를 누르면 방송을 끝낸다.
      tracks.video.addEventListener('ended', () => {
        if (tracksRef.current.video === tracks.video) end('화면 공유가 중지되어 방송을 종료했습니다.')
      })
    }
    const live: LiveSession = {
      broadcast: await api.broadcast(broadcast.id).catch(() => broadcast),
      source, room, videoTrack: tracks.video ?? null,
      viewers: 0, reconnecting: false, micMuted: false,
      hasMic: !!tracks.mic, hasSystemAudio: !!tracks.screenAudio, facing,
    }
    sessionRef.current = live
    setSession(live)
    countViewers()
  }, [cleanup, end, update])

  const toggleMic = useCallback(() => {
    const mic = tracksRef.current.mic
    if (!mic) return
    mic.enabled = !mic.enabled
    update({ micMuted: !mic.enabled })
  }, [update])

  const replaceCamera = useCallback(async (facing: 'user' | 'environment') => {
    const room = roomRef.current
    const lk = lkRef.current
    if (!room || !lk) return
    const s = await navigator.mediaDevices.getUserMedia({ video: cameraConstraints(facing) })
    const next = s.getVideoTracks()[0]
    const pub = room.localParticipant.getTrackPublication(lk.Track.Source.Camera)
    if (pub?.track) await pub.track.replaceTrack(next)
    tracksRef.current.video?.stop()
    tracksRef.current.video = next
    update({ videoTrack: next, facing })
  }, [update])

  const switchCamera = useCallback(async () => {
    const s = sessionRef.current
    if (!s || s.source !== 'CAMERA') return
    await replaceCamera(s.facing === 'user' ? 'environment' : 'user')
  }, [replaceCamera])

  // 모바일에서 앱 전환·화면 잠금으로 카메라/마이크가 끊겼다가 돌아오면 다시 잡아서 이어서 송출한다.
  useEffect(() => {
    const onVisible = async () => {
      const s = sessionRef.current
      const room = roomRef.current
      if (document.visibilityState !== 'visible' || !s || !room) return
      try {
        if (s.source === 'CAMERA' && tracksRef.current.video?.readyState === 'ended') {
          await replaceCamera(s.facing)
        }
        if (tracksRef.current.mic?.readyState === 'ended') {
          const m = (await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })).getAudioTracks()[0]
          const pub = lkRef.current && room.localParticipant.getTrackPublication(lkRef.current.Track.Source.Microphone)
          if (pub?.track) await pub.track.replaceTrack(m)
          m.enabled = !s.micMuted
          tracksRef.current.mic = m
        }
      } catch { /* 권한 등으로 실패하면 서버 유예 시간 뒤 자동 종료 */ }
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [replaceCamera])

  // 영상은 LiveKit 으로만 흐르므로 API 서버에는 요청이 없다.
  // 무료 호스팅이 15분 무요청 시 잠들지 않도록 송출 중에는 주기적으로 상태를 조회한다.
  const liveId = session?.broadcast.id
  useEffect(() => {
    if (!liveId) return
    const t = setInterval(() => {
      api.broadcast(liveId).then(b => {
        if (b.status === 'ENDED') end('서버에서 방송이 종료되었습니다.')
        else update({ broadcast: b })
      }).catch(() => {})
    }, 4 * 60 * 1000)
    return () => clearInterval(t)
  }, [liveId, end, update])

  // 송출 중 탭을 닫거나 새로고침하면 경고
  useEffect(() => {
    if (!liveId) return
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [liveId])

  return (
    <SessionContext.Provider value={{ session, ended, goLive, end, toggleMic, switchCamera }}>
      {children}
    </SessionContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useBroadcastSession() {
  const ctx = useContext(SessionContext)
  if (!ctx) throw new Error('BroadcastSessionProvider missing')
  return ctx
}
