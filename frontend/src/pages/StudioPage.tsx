import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Room, RoomEvent, Track, AudioPresets, VideoPresets, type LocalTrackPublication } from 'livekit-client'
import { api, ApiError, errorMessage, type Broadcast, type BroadcastSource } from '../lib/api'
import { useAuth } from '../lib/auth'
import { canShareScreen, isMobileDevice } from '../lib/format'
import { StatusBadge } from '../components/StatusBadge'
import { ChatPanel } from '../components/ChatPanel'

type Phase = 'idle' | 'ready' | 'connecting' | 'live' | 'ended'

/**
 * 송출 화면. 미디어(화면/카메라/마이크)를 먼저 확보한 뒤 서버에 시작을 요청하고,
 * 서버가 발급한 송출 토큰으로 미디어 서버에 접속해 트랙을 publish 한다.
 */
export function StudioPage() {
  const { id } = useParams()
  const broadcastId = Number(id)
  const { me } = useAuth()
  const navigate = useNavigate()

  const [broadcast, setBroadcast] = useState<Broadcast | null>(null)
  const [source, setSource] = useState<BroadcastSource>(canShareScreen() ? 'SCREEN' : 'CAMERA')
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState('')
  const [hasSystemAudio, setHasSystemAudio] = useState(false)
  const [micMuted, setMicMuted] = useState(false)
  const [hasMic, setHasMic] = useState(false)
  const [facing, setFacing] = useState<'user' | 'environment'>('user')
  const [room, setRoom] = useState<Room | null>(null)

  const previewRef = useRef<HTMLVideoElement>(null)
  const streamsRef = useRef<{ video?: MediaStreamTrack; screenAudio?: MediaStreamTrack; mic?: MediaStreamTrack }>({})
  const roomRef = useRef<Room | null>(null)
  const endingRef = useRef(false)

  useEffect(() => {
    api.broadcast(broadcastId).then(b => {
      setBroadcast(b)
      if (b.status === 'ENDED') setPhase('ended')
      if (b.source) setSource(b.source)
    }).catch(e => setError(errorMessage(e)))
  }, [broadcastId])

  const stopLocalTracks = useCallback(() => {
    const s = streamsRef.current
    ;[s.video, s.screenAudio, s.mic].forEach(t => t?.stop())
    streamsRef.current = {}
    if (previewRef.current) previewRef.current.srcObject = null
  }, [])

  const endBroadcast = useCallback(async (reason?: string) => {
    if (endingRef.current) return
    endingRef.current = true
    try {
      const b = await api.end(broadcastId)
      setBroadcast(b)
    } catch (e) {
      // 이미 종료된 경우(서버 자동 종료 등)는 무시
      if (!(e instanceof ApiError && e.code === 'BROADCAST_NOT_LIVE')) {
        setError(errorMessage(e))
      }
    } finally {
      await roomRef.current?.disconnect()
      roomRef.current = null
      setRoom(null)
      stopLocalTracks()
      setPhase('ended')
      if (reason) setError(reason)
    }
  }, [broadcastId, stopLocalTracks])

  // 페이지를 떠나면 장치와 연결을 정리한다. (방송 자체는 유예 시간 뒤 서버가 자동 종료)
  useEffect(() => () => {
    roomRef.current?.disconnect()
    stopLocalTracks()
  }, [stopLocalTracks])

  // 영상은 LiveKit 으로만 흐르므로 API 서버에는 요청이 없다.
  // 무료 호스팅이 15분 무요청 시 잠들지 않도록 방송 중에는 주기적으로 상태를 조회한다.
  useEffect(() => {
    if (phase !== 'live') return
    const t = setInterval(() => {
      api.broadcast(broadcastId).then(b => {
        setBroadcast(b)
        if (b.status === 'ENDED') endBroadcast('서버에서 방송이 종료되었습니다.')
      }).catch(() => {})
    }, 4 * 60 * 1000)
    return () => clearInterval(t)
  }, [phase, broadcastId, endBroadcast])

  useEffect(() => {
    if (phase !== 'live') return
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [phase])

  async function prepareMedia() {
    setError('')
    stopLocalTracks()
    try {
      if (source === 'SCREEN') {
        const display = await navigator.mediaDevices.getDisplayMedia({
          video: { frameRate: 30 },
          // 탭 소리 / 시스템 소리. 브라우저가 지원하지 않으면 영상만 들어온다.
          audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
          systemAudio: 'include',
          selfBrowserSurface: 'exclude',
        } as DisplayMediaStreamOptions)
        const video = display.getVideoTracks()[0]
        const screenAudio = display.getAudioTracks()[0]
        const mic = await getMic()
        streamsRef.current = { video, screenAudio, mic }
        setHasSystemAudio(!!screenAudio)
        setHasMic(!!mic)
        // 브라우저의 "공유 중지"를 누르면 방송을 끝낸다.
        video.addEventListener('ended', () => {
          if (roomRef.current) endBroadcast('화면 공유가 중지되어 방송을 종료했습니다.')
          else { stopLocalTracks(); setPhase('idle') }
        })
      } else {
        const cam = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: { echoCancellation: true, noiseSuppression: true },
        })
        streamsRef.current = { video: cam.getVideoTracks()[0], mic: cam.getAudioTracks()[0] }
        setHasSystemAudio(false)
        setHasMic(cam.getAudioTracks().length > 0)
      }
      if (previewRef.current && streamsRef.current.video) {
        previewRef.current.srcObject = new MediaStream([streamsRef.current.video])
      }
      setPhase('ready')
    } catch (e) {
      stopLocalTracks()
      setError(mediaErrorMessage(e))
    }
  }

  async function goLive() {
    if (!broadcast) return
    setError('')
    setPhase('connecting')
    endingRef.current = false
    try {
      const conn = broadcast.status === 'LIVE'
        ? await api.publisherToken(broadcastId)   // 새로고침 후 재접속
        : await api.start(broadcastId, source)
      const room = new Room({ adaptiveStream: false, dynacast: true, publishDefaults: { simulcast: true } })
      room.on(RoomEvent.Disconnected, () => {
        // 서버가 방을 정리한 경우(종료/자동 종료)
        if (!endingRef.current) {
          roomRef.current = null
          setRoom(null)
          stopLocalTracks()
          setPhase('ended')
          api.broadcast(broadcastId).then(setBroadcast).catch(() => {})
        }
      })
      await room.connect(conn.serverUrl, conn.token)
      roomRef.current = room
      setRoom(room)

      const { video, screenAudio, mic } = streamsRef.current
      if (video) {
        await room.localParticipant.publishTrack(video, source === 'SCREEN'
          ? { source: Track.Source.ScreenShare, videoEncoding: { maxBitrate: 3_000_000, maxFramerate: 30 }, simulcast: false }
          : { source: Track.Source.Camera, videoEncoding: VideoPresets.h720.encoding })
      }
      if (screenAudio) {
        await room.localParticipant.publishTrack(screenAudio, {
          source: Track.Source.ScreenShareAudio, audioPreset: AudioPresets.musicHighQualityStereo, dtx: false, red: false,
        })
      }
      if (mic) {
        await room.localParticipant.publishTrack(mic, { source: Track.Source.Microphone })
      }
      setBroadcast(await api.broadcast(broadcastId))
      setPhase('live')
    } catch (e) {
      setError(errorMessage(e))
      await roomRef.current?.disconnect()
      roomRef.current = null
      setRoom(null)
      setPhase('ready')
      api.broadcast(broadcastId).then(setBroadcast).catch(() => {})
    }
  }

  function toggleMic() {
    const mic = streamsRef.current.mic
    if (!mic) return
    mic.enabled = !mic.enabled
    setMicMuted(!mic.enabled)
  }

  async function switchCamera() {
    const next = facing === 'user' ? 'environment' : 'user'
    setFacing(next)
    if (source !== 'CAMERA' || !streamsRef.current.video) return
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: next, width: { ideal: 1280 }, height: { ideal: 720 } } })
      const newTrack = s.getVideoTracks()[0]
      const pub: LocalTrackPublication | undefined = roomRef.current?.localParticipant.getTrackPublication(Track.Source.Camera)
      if (pub?.track) await pub.track.replaceTrack(newTrack)
      streamsRef.current.video?.stop()
      streamsRef.current.video = newTrack
      if (previewRef.current) previewRef.current.srcObject = new MediaStream([newTrack])
    } catch (e) { setError(mediaErrorMessage(e)) }
  }

  if (!broadcast) return <p className="muted">{error || '불러오는 중…'}</p>
  if (me && broadcast.owner.id !== me.id) {
    return <p className="error">방송 소유자만 송출할 수 있습니다. <Link to={`/broadcasts/${broadcastId}`}>시청하러 가기</Link></p>
  }

  const mobile = isMobileDevice()
  return (
    <div className="watch">
      <section className="watch-main">
        <div className="studio-head">
          <h1>{broadcast.title} <StatusBadge status={broadcast.status} /></h1>
          <Link to={`/broadcasts/${broadcastId}`} className="muted small">시청 화면 보기 →</Link>
        </div>

        <div className="player">
          <video ref={previewRef} autoPlay playsInline muted className={source === 'CAMERA' && facing === 'user' ? 'mirror' : ''} />
          {phase === 'idle' && <div className="player-overlay">{source === 'SCREEN' ? '공유할 화면을 선택하세요' : '카메라를 켜세요'}</div>}
          {phase === 'ended' && <div className="player-overlay">방송이 종료되었습니다.</div>}
          {phase === 'live' && <span className="onair">● ON AIR</span>}
        </div>

        {error && <p className="error">{error}</p>}

        {phase !== 'ended' && (
          <div className="studio-controls">
            {(phase === 'idle' || phase === 'ready') && broadcast.status !== 'LIVE' && (
              <div className="segmented">
                <button className={source === 'SCREEN' ? 'on' : ''} disabled={!canShareScreen()}
                  onClick={() => { setSource('SCREEN'); stopLocalTracks(); setPhase('idle') }}>
                  🖥 PC 화면 공유
                </button>
                <button className={source === 'CAMERA' ? 'on' : ''}
                  onClick={() => { setSource('CAMERA'); stopLocalTracks(); setPhase('idle') }}>
                  📷 카메라
                </button>
              </div>
            )}

            {(phase === 'idle' || phase === 'ready') && (
              <button className="btn" onClick={prepareMedia}>
                {phase === 'ready' ? '다시 선택' : source === 'SCREEN' ? '화면·마이크 선택' : '카메라·마이크 켜기'}
              </button>
            )}
            {phase === 'ready' && (
              <button className="btn btn-primary" onClick={goLive}>
                {broadcast.status === 'LIVE' ? '방송 다시 연결' : '방송 시작'}
              </button>
            )}
            {phase === 'connecting' && <button className="btn" disabled>연결 중…</button>}
            {phase === 'live' && (
              <>
                <button className="btn" onClick={toggleMic}>{micMuted ? '🎙 마이크 켜기' : '🔇 마이크 끄기'}</button>
                {source === 'CAMERA' && <button className="btn" onClick={switchCamera}>🔄 카메라 전환</button>}
                <button className="btn btn-danger" onClick={() => { if (confirm('방송을 종료할까요?')) endBroadcast() }}>방송 종료</button>
              </>
            )}
            {source === 'CAMERA' && phase === 'ready' && (
              <button className="btn" onClick={switchCamera}>🔄 {facing === 'user' ? '후면' : '전면'} 카메라</button>
            )}
          </div>
        )}

        {phase === 'ended' && (
          <div className="studio-controls">
            <button className="btn btn-primary" onClick={() => navigate('/broadcasts/new')}>새 방송 만들기</button>
            <Link className="btn" to={`/broadcasts/${broadcastId}`}>방송 상세</Link>
          </div>
        )}

        {source === 'SCREEN' && phase !== 'ended' && (
          <ul className="hint">
            <li>탭을 공유할 때 <b>“탭 오디오도 공유”</b>, 전체 화면은 <b>“시스템 오디오 공유”</b>를 켜면 소리가 함께 송출됩니다. (Chrome/Edge 권장)</li>
            <li>시스템 소리는 Windows Chrome/Edge 전체 화면 공유에서만 지원됩니다. macOS는 탭 오디오를 이용하세요.</li>
            {phase !== 'idle' && <li>화면 소리: <b>{hasSystemAudio ? '함께 송출' : '없음'}</b> · 마이크: <b>{hasMic ? '켜짐' : '없음'}</b></li>}
          </ul>
        )}
        {source === 'CAMERA' && mobile && phase === 'idle' && (
          <ul className="hint"><li>카메라·마이크 권한을 허용해 주세요. 방송 중에는 화면을 켜 둔 상태로 유지해야 합니다.</li></ul>
        )}
        {phase === 'live' && (
          <ul className="hint"><li>연결이 잠시 끊겨도 자동 재연결됩니다. 오래 끊기면 서버가 방송을 자동 종료합니다.</li></ul>
        )}
      </section>
      {(phase === 'live' || broadcast.status === 'LIVE') && <ChatPanel broadcastId={broadcastId} room={room} />}
    </div>
  )
}

async function getMic(): Promise<MediaStreamTrack | undefined> {
  try {
    const s = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
    return s.getAudioTracks()[0]
  } catch {
    return undefined // 마이크가 없거나 거부해도 화면 방송은 가능
  }
}

function mediaErrorMessage(e: unknown): string {
  if (e instanceof DOMException) {
    if (e.name === 'NotAllowedError') return '권한이 거부되었거나 선택을 취소했습니다. 브라우저 설정에서 권한을 허용해 주세요.'
    if (e.name === 'NotFoundError') return '사용할 수 있는 장치가 없습니다.'
    if (e.name === 'NotReadableError') return '다른 앱이 장치를 사용 중입니다.'
  }
  if (!window.isSecureContext) return 'HTTPS 주소에서만 카메라·화면 공유를 사용할 수 있습니다.'
  return errorMessage(e)
}
