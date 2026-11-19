import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage, type Broadcast, type BroadcastSource } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBroadcastSession, type MediaTracks } from '../lib/broadcastSession'
import { canShareScreen, isMobileDevice } from '../lib/format'
import { StatusBadge } from '../components/StatusBadge'
import { ChatPanel } from '../components/ChatPanel'
import { DonationBox } from '../components/DonationBox'
import { QoePanel } from '../components/QoePanel'
import { RecordingControl } from '../components/RecordingControl'

/**
 * 송출 화면. 장치(화면/카메라/마이크)를 먼저 고른 뒤 송출을 시작하면
 * 송출 세션이 앱 전체(BroadcastSessionProvider)로 넘어간다. 이 화면을 떠나도 방송은 계속된다.
 */
export function StudioPage() {
  const { id } = useParams()
  const broadcastId = Number(id)
  const { me } = useAuth()
  const navigate = useNavigate()
  const live = useBroadcastSession()
  const mySession = live.session?.broadcast.id === broadcastId ? live.session : null
  const otherSession = live.session && !mySession ? live.session : null
  const endedHere = live.ended?.broadcastId === broadcastId ? live.ended : null

  const [broadcast, setBroadcast] = useState<Broadcast | null>(null)
  const [source, setSource] = useState<BroadcastSource>(canShareScreen() ? 'SCREEN' : 'CAMERA')
  const [prepared, setPrepared] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [error, setError] = useState('')
  const [hasSystemAudio, setHasSystemAudio] = useState(false)
  const [hasMic, setHasMic] = useState(false)
  const [facing, setFacing] = useState<'user' | 'environment'>('user')
  const [recordingEnabled, setRecordingEnabled] = useState(false)
  const [recordOnStart, setRecordOnStart] = useState(true)

  const previewRef = useRef<HTMLVideoElement>(null)
  /** 송출 시작 전 미리보기용 장치. 송출이 시작되면 세션으로 넘긴다. */
  const draftRef = useRef<MediaTracks>({})
  const onScreenEndedRef = useRef<(() => void) | null>(null)

  useEffect(() => { api.config().then(c => setRecordingEnabled(c.recordingEnabled)).catch(() => {}) }, [])

  useEffect(() => {
    api.broadcast(broadcastId).then(b => {
      setBroadcast(b)
      if (b.source) setSource(b.source)
    }).catch(e => setError(errorMessage(e)))
  }, [broadcastId, endedHere])

  const stopDraft = useCallback(() => {
    const d = draftRef.current
    if (d.video && onScreenEndedRef.current) d.video.removeEventListener('ended', onScreenEndedRef.current)
    ;[d.video, d.screenAudio, d.mic].forEach(t => t?.stop())
    draftRef.current = {}
  }, [])

  // 미리보기는 송출 전에는 고른 장치, 송출 중에는 세션의 영상 트랙을 보여준다.
  // <video> 는 방송 정보를 불러온 뒤에야 그려지므로, 요소가 생기는 순간(callback ref)에 연결한다.
  const previewTrack = mySession?.videoTrack ?? null
  const attachPreview = useCallback((el: HTMLVideoElement | null) => {
    previewRef.current = el
    if (!el || !previewTrack) return
    const current = el.srcObject instanceof MediaStream ? el.srcObject.getVideoTracks()[0] : undefined
    if (current !== previewTrack) el.srcObject = new MediaStream([previewTrack])
  }, [previewTrack])

  // 송출을 시작하지 않고 떠나면 미리보기 장치만 정리한다.
  useEffect(() => () => stopDraft(), [stopDraft])

  async function prepareMedia() {
    setError('')
    stopDraft()
    setPrepared(false)
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
        draftRef.current = { video, screenAudio, mic }
        setHasSystemAudio(!!screenAudio)
        setHasMic(!!mic)
        // 송출 시작 전에 공유를 멈추면 다시 고르게 한다.
        onScreenEndedRef.current = () => { stopDraft(); setPrepared(false) }
        video.addEventListener('ended', onScreenEndedRef.current)
      } else {
        const cam = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: { echoCancellation: true, noiseSuppression: true },
        })
        draftRef.current = { video: cam.getVideoTracks()[0], mic: cam.getAudioTracks()[0] }
        setHasSystemAudio(false)
        setHasMic(cam.getAudioTracks().length > 0)
      }
      if (previewRef.current && draftRef.current.video) {
        previewRef.current.srcObject = new MediaStream([draftRef.current.video])
      }
      setPrepared(true)
    } catch (e) {
      stopDraft()
      setError(mediaErrorMessage(e))
    }
  }

  async function goLive() {
    if (!broadcast) return
    setError('')
    setConnecting(true)
    const tracks = draftRef.current
    // 장치 소유권을 세션으로 넘긴다(이 화면이 사라져도 멈추지 않게)
    if (tracks.video && onScreenEndedRef.current) tracks.video.removeEventListener('ended', onScreenEndedRef.current)
    draftRef.current = {}
    try {
      await live.goLive(broadcast, source, tracks, facing, recordingEnabled && recordOnStart)
      setPrepared(false)
    } catch (e) {
      draftRef.current = tracks // 실패하면 다시 미리보기 상태로
      setError(errorMessage(e))
      api.broadcast(broadcastId).then(setBroadcast).catch(() => {})
    } finally {
      setConnecting(false)
    }
  }

  async function switchDraftCamera() {
    const next = facing === 'user' ? 'environment' : 'user'
    setFacing(next)
    if (!draftRef.current.video) return
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: { facingMode: next, width: { ideal: 1280 }, height: { ideal: 720 } } })
      draftRef.current.video.stop()
      draftRef.current.video = s.getVideoTracks()[0]
      if (previewRef.current) previewRef.current.srcObject = new MediaStream([draftRef.current.video])
    } catch (e) { setError(mediaErrorMessage(e)) }
  }

  if (!broadcast) return <p className="muted">{error || '불러오는 중…'}</p>
  if (me && broadcast.owner.id !== me.id) {
    return <p className="error">방송 소유자만 송출할 수 있습니다. <Link to={`/broadcasts/${broadcastId}`}>시청하러 가기</Link></p>
  }

  const isLive = !!mySession
  const isEnded = !isLive && (broadcast.status === 'ENDED' || !!endedHere)
  const curSource = mySession?.source ?? source
  const curFacing = mySession?.facing ?? facing
  const mobile = isMobileDevice()

  return (
    <div className="watch">
      <section className="watch-main">
        <div className="studio-head">
          <h1>{broadcast.title} <StatusBadge status={isLive ? 'LIVE' : isEnded ? 'ENDED' : broadcast.status} /></h1>
          <Link to={`/broadcasts/${broadcastId}`} className="muted small">시청 화면 보기 →</Link>
        </div>

        {otherSession && (
          <p className="error">
            다른 방송(“{otherSession.broadcast.title}”)을 송출 중입니다.{' '}
            <Link to={`/broadcasts/${otherSession.broadcast.id}/studio`}>그 방송으로 이동</Link>
          </p>
        )}

        <div className="player">
          <video ref={attachPreview} autoPlay playsInline muted className={curSource === 'CAMERA' && curFacing === 'user' ? 'mirror' : ''} />
          {!isLive && !prepared && !isEnded && <div className="player-overlay">{source === 'SCREEN' ? '공유할 화면을 선택하세요' : '카메라를 켜세요'}</div>}
          {isEnded && <div className="player-overlay">{endedHere?.reason ?? '방송이 종료되었습니다.'}</div>}
          {isLive && <span className="onair">● ON AIR</span>}
          {isLive && <span className="viewers">👁 {mySession.viewers}</span>}
          {isLive && mySession.reconnecting && <div className="player-overlay"><span className="spinner">네트워크가 불안정해 다시 연결하는 중… (송출 유지 중)</span></div>}
        </div>

        {error && <p className="error">{error}</p>}
        {isLive && recordingEnabled && <RecordingControl broadcastId={broadcastId} />}
        {isLive && <QoePanel broadcastId={broadcastId} />}
        {isLive && <DonationBox broadcast={broadcast} />}

        {!isEnded && !otherSession && (
          <div className="studio-controls">
            {!isLive && broadcast.status !== 'LIVE' && (
              <div className="segmented">
                <button className={source === 'SCREEN' ? 'on' : ''} disabled={!canShareScreen() || connecting}
                  onClick={() => { setSource('SCREEN'); stopDraft(); setPrepared(false) }}>
                  🖥 PC 화면 공유
                </button>
                <button className={source === 'CAMERA' ? 'on' : ''} disabled={connecting}
                  onClick={() => { setSource('CAMERA'); stopDraft(); setPrepared(false) }}>
                  📷 카메라
                </button>
              </div>
            )}
            {!isLive && !connecting && (
              <button className="btn" onClick={prepareMedia}>
                {prepared ? '다시 선택' : source === 'SCREEN' ? '화면·마이크 선택' : '카메라·마이크 켜기'}
              </button>
            )}
            {!isLive && prepared && !connecting && recordingEnabled && broadcast.status !== 'LIVE' && (
              <label className="small rec-check">
                <input type="checkbox" checked={recordOnStart} onChange={e => setRecordOnStart(e.target.checked)} /> 시작과 함께 녹화
              </label>
            )}
            {!isLive && prepared && !connecting && (
              <button className="btn btn-primary" onClick={goLive}>
                {broadcast.status === 'LIVE' ? '방송 다시 연결' : '방송 시작'}
              </button>
            )}
            {!isLive && source === 'CAMERA' && prepared && !connecting && (
              <button className="btn" onClick={switchDraftCamera}>🔄 {facing === 'user' ? '후면' : '전면'} 카메라</button>
            )}
            {connecting && <button className="btn" disabled><span className="spinner">연결 중…</span></button>}
            {isLive && (
              <>
                <button className="btn" onClick={live.toggleMic} disabled={!mySession.hasMic}>
                  {mySession.micMuted ? '🎙 마이크 켜기' : '🔇 마이크 끄기'}
                </button>
                {mySession.source === 'CAMERA' && (
                  <button className="btn" onClick={() => live.switchCamera().catch(e => setError(mediaErrorMessage(e)))}>🔄 카메라 전환</button>
                )}
                <button className="btn btn-danger" onClick={() => { if (confirm('방송을 종료할까요?')) live.end() }}>방송 종료</button>
              </>
            )}
          </div>
        )}

        {isEnded && (
          <div className="studio-controls">
            <button className="btn btn-primary" onClick={() => navigate('/broadcasts/new')}>새 방송 만들기</button>
            <Link className="btn" to={`/broadcasts/${broadcastId}`}>방송 상세</Link>
          </div>
        )}

        {curSource === 'SCREEN' && !isEnded && (
          <ul className="hint">
            <li>탭을 공유할 때 <b>“탭 오디오도 공유”</b>, 전체 화면은 <b>“시스템 오디오 공유”</b>를 켜면 소리가 함께 송출됩니다. (Chrome/Edge 권장)</li>
            <li>시스템 소리는 Windows Chrome/Edge 전체 화면 공유에서만 지원됩니다. macOS는 탭 오디오를 이용하세요.</li>
            {(prepared || isLive) && (
              <li>화면 소리: <b>{(mySession?.hasSystemAudio ?? hasSystemAudio) ? '함께 송출' : '없음'}</b> · 마이크: <b>{(mySession?.hasMic ?? hasMic) ? '켜짐' : '없음'}</b></li>
            )}
          </ul>
        )}
        {curSource === 'CAMERA' && mobile && !isLive && !isEnded && (
          <ul className="hint"><li>카메라·마이크 권한을 허용해 주세요.</li></ul>
        )}
        {isLive && (
          <ul className="hint">
            <li><b>이 화면을 떠나 사이트 안의 다른 페이지로 이동해도 방송은 계속됩니다.</b> 상단 빨간 바에서 다시 돌아올 수 있어요.</li>
            <li>탭을 닫거나 새로고침하면 송출이 끊기고, 45초 안에 돌아와 “방송 다시 연결”을 누르지 않으면 자동 종료됩니다.</li>
            {mySession.source === 'CAMERA' && <li>휴대폰에서 다른 앱으로 전환하면 카메라가 잠시 멈추고, 돌아오면 자동으로 다시 송출합니다.</li>}
          </ul>
        )}
      </section>
      {(isLive || broadcast.status === 'LIVE') && <ChatPanel broadcastId={broadcastId} />}
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
