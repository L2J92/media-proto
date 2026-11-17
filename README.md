# media-proto · 실시간 방송 플랫폼

PC 화면 공유(탭·시스템 소리 + 마이크)와 휴대폰 카메라로 라이브 방송을 송출하고, 다른 기기에서 실시간으로 시청하는 웹 서비스입니다.

| 항목 | 내용 |
|---|---|
| 서비스 주소 | **https://media-proto.onrender.com** |
| 시연 영상 | **(링크 기입)** — PC 송출·시청·종료 / 모바일 송출·시청·종료 |
| 테스트 계정 | 로그인 화면의 **[방송자 계정] [방송자2 계정] [시청자 계정]** 버튼으로 비밀번호 없이 바로 로그인 |

> 무료 호스팅(Render Free)을 사용합니다. 15분간 접속이 없으면 서버가 잠들어 **첫 접속 시 약 1분** 로딩될 수 있습니다.

## 체크리스트 대응표
| 요구사항 | 확인 방법 | 자동 테스트 |
|---|---|---|
| PC 화면 캡처 실시간 송출 | [1-1](#1-1-pc-라이브-방송-화면-공유--소리) | — (실기기, 시연 영상) |
| 탭/시스템 소리 + 마이크 함께 송출 | [1-1](#1-1-pc-라이브-방송-화면-공유--소리) 3단계, 송출 화면에 "화면 소리·마이크" 상태 표시 | — |
| 방송 시작·종료 시 송출 연결 생성·종료 | 시작 = LiveKit 방 생성 + 송출 토큰, 종료 = **방 삭제로 연결 강제 종료** ([방송 상태 관리](#방송-상태-관리)) | `BroadcastAuthorizationTest` |
| 휴대폰 카메라·마이크 송출, 모바일 시작·종료 | [1-2](#1-2-모바일-라이브-방송-카메라--마이크) | — (실기기, 시연 영상) |
| 방송 정보 등록·조회·수정·삭제 | [1-3](#1-3-방송-관리--인증인가-확인), [API](#3-api-요약) | `BroadcastAuthorizationTest` |
| 목록·상세, PC/모바일 송출을 다른 기기에서 수신·재생 | [1-1](#1-1-pc-라이브-방송-화면-공유--소리) 5단계, [1-2](#1-2-모바일-라이브-방송-카메라--마이크) 4단계 | — |
| 방송 중/종료 상태 관리, 종료 시 송출 중단 | `READY→LIVE→ENDED`, 송출자 이탈 45초 자동 종료, 웹훅 + 주기 조회 보정 | `BroadcastAuthorizationTest`, `WebhookTest` |
| 방송 정보 영구 저장, 재시작 후 조회 | PostgreSQL(Neon) + Flyway, 세션도 DB 저장 | `PostgresIntegrationTest` |
| 회원가입·로그인·로그아웃·인증 상태 | [인증](#인증) | `AuthFlowTest` |
| 비로그인 조회·시청 / 로그인 사용자만 등록·송출 | [1-3](#1-3-방송-관리--인증인가-확인) 표 | `BroadcastAuthorizationTest` (비로그인/다른 사용자/소유자) |
| 수정·삭제·송출 제어 시 서버의 소유자 권한 검증 | 다른 사용자 → `403 NOT_BROADCAST_OWNER` | `BroadcastAuthorizationTest` |
| (선택) 댓글 작성·조회·수정·삭제, 작성자 권한 | [1-4](#1-4-채팅-선택-요구사항--댓글) | `ChatTest` |
| (선택) 도네이션: 대상·금액 선택, 테스트 결제 | [1-5](#1-5-도네이션-선택-요구사항--포인트-지갑--토스페이먼츠-테스트-결제) | `WalletDonationTest` |
| (선택) 승인 결과·금액·후원 대상 서버 검증 | [결제 검증](#도네이션-결제-검증과-중복-적립-방지) | `WalletDonationTest` |
| (선택) 중복 승인 통지 1회 적립, 방송별 내역·합계 | 주문 잠금 + 1회 상태 전이 + unique | `WalletDonationTest`, `PostgresIntegrationTest`(동시 요청) |
| (선택) 녹화 저장, 목록·상세·재생, 위치 영구 저장 | [1-6](#1-6-녹화-및-다시보기-선택-요구사항--hls) | `RecordingTest` |

---

## 1. 기능 확인 방법

### 1-1. PC 라이브 방송 (화면 공유 + 소리)
1. PC Chrome/Edge로 접속 → 로그인 → **[방송자 계정]**
2. 상단 **방송하기** → 제목 입력 → **만들고 송출 화면으로**
3. **🖥 PC 화면 공유** 선택 → **화면·마이크 선택**
   - 탭 공유: 공유 창에서 **“탭 오디오도 공유”** 체크
   - 전체 화면(Windows): **“시스템 오디오 공유”** 체크
   - 마이크 권한 허용
4. 미리보기 확인 후 **방송 시작** → `● ON AIR` 표시
   - 송출 중에 사이트 안의 다른 페이지(목록·다른 방송 등)로 이동해도 **방송이 계속**되며, 상단 빨간 바에서 시청자 수 확인·스튜디오 복귀·종료를 할 수 있습니다.
5. **다른 기기(휴대폰 등)**에서 서비스 주소 접속 → 방송 목록의 `LIVE` 방송 클릭 → 영상 재생, **🔊 소리 켜기** 클릭 시 화면 소리 + 마이크 음성 재생
   - 플레이어에서 시청자 수(👁), 볼륨·음소거, 전체 화면을 사용할 수 있고, 네트워크가 끊기면 “다시 연결하는 중” 표시 후 자동 복구됩니다.
6. 송출 화면에서 **방송 종료** → 시청 화면이 “방송이 종료되었습니다”로 바뀌고 목록에서 `종료` 상태로 표시
   - 브라우저의 “공유 중지”를 눌러도 방송이 종료됩니다.

### 1-2. 모바일 라이브 방송 (카메라 + 마이크)
1. 휴대폰 브라우저(iOS Safari / Android Chrome)로 접속 → **[방송자2 계정]**
2. **방송하기** → 제목 입력 → 송출 화면에서 **📷 카메라** → **카메라·마이크 켜기** → 권한 허용
3. 필요하면 **🔄 후면 카메라**로 전환 → **방송 시작**
4. PC 등 다른 기기에서 해당 방송을 열어 영상·음성 확인
5. 휴대폰에서 **방송 종료** → 시청 화면 종료 확인

### 1-3. 방송 관리 / 인증·인가 확인
| 시나리오 | 기대 결과 |
|---|---|
| 비로그인으로 목록·상세·시청 | ✅ 가능 |
| 비로그인으로 방송하기 / 채팅 작성 | 로그인 화면으로 이동 (API는 `401`) |
| 소유자가 제목 수정 / 삭제 / 송출 시작·종료 | ✅ 가능 (방송 중 삭제 시 송출 중단 후 삭제) |
| 다른 사용자가 남의 방송 수정·삭제·송출·종료 | `403 NOT_BROADCAST_OWNER` |
| 다른 사용자가 남의 채팅 수정·삭제 | `403 NOT_CHAT_AUTHOR` (삭제는 채널 소유자·매니저만 예외) |
| 서버 재시작 후 | 방송 정보·채팅·로그인 세션 유지 (PostgreSQL 저장) |

### 1-4. 채팅 (선택 요구사항 · 댓글)
- 로그인 사용자는 방송별로 채팅을 작성하고, **본인 채팅만** 수정·삭제할 수 있습니다. (서버에서 작성자 검증, 남의 채팅은 `403`)
- 방송 중에는 다른 시청자 화면에 실시간으로 반영되고, 종료된 방송에서는 댓글처럼 조회·작성됩니다.
- 전송 즉시 입력창이 비워지고 내 메시지가 먼저 표시됩니다(실패 시 되돌리고 입력 복구). 5초에 5개 초과 시 도배 방지(`429`).

### 1-5. 도네이션 (선택 요구사항 · 포인트 지갑 + 토스페이먼츠 테스트 결제)
계정마다 **포인트 지갑**(1P = 1원)이 있습니다.
1. **[시청자 계정]**으로 로그인 → 방송 상세의 **💰 후원하기** → 금액·응원 메시지 입력
2. 포인트가 **부족하면** 버튼이 **“○원 충전하고 바로 후원”** 으로 바뀝니다 → 토스 테스트 결제창 → 결제
   - 결제 한 번으로 **충전 + 해당 방송 후원**이 함께 처리되고 “후원 완료” 화면이 나옵니다.
3. 포인트가 **충분하면** 결제창 없이 **즉시 후원**됩니다.
4. 상단 **💎 잔액** → **내 지갑**에서 따로 충전하고 충전·후원·받은 후원 내역을 볼 수 있습니다.
5. 방송 화면에 **누적 후원 금액·건수·최근 후원**이 표시되고, 방송 중이면 💰 알림이 모든 시청자·송출자에게 실시간으로 뜹니다. 방송자 지갑에는 받은 후원이 적립됩니다.
- 테스트 모드라 실제 결제되지 않습니다. 자신의 방송에는 후원할 수 없습니다.

### 1-6. 녹화 및 다시보기 (선택 요구사항 · HLS)
1. 방송을 시작하면 서버가 LiveKit Egress로 **HLS 녹화**(6초 세그먼트 + `index.m3u8`)를 시작해 S3 호환 저장소에 올립니다.
2. 방송을 종료하고 약 1분 뒤 상단 **다시보기** 메뉴와 방송 상세 화면에 녹화 영상이 나타납니다.
3. 재생: Safari는 네이티브 HLS, Chrome/Edge 등은 hls.js로 재생합니다(탐색·이어보기 가능).
- 녹화 완료는 LiveKit 웹훅(`egress_ended`)과 **30초 주기 상태 조회**를 함께 사용해, 웹훅이 유실돼도 "처리 중"에 머물지 않습니다.

> 아래 1-7 ~ 1-9는 요구사항 외 **추가 구현**입니다.

### 1-7. 화질 선택 · 시청 품질(QoE) 모니터링
- 시청 플레이어 하단 **⚙ 화질**: 자동 / 고화질 / 중간 / 저화질 (송출은 시뮬캐스트로 여러 화질을 동시에 보내고, 시청자가 레이어를 고름. 자동은 대역폭에 맞춰 SFU가 조절)
- **📊 통계**(유튜브 '전문가용 통계'처럼): 해상도·FPS·코덱·비트레이트·패킷 손실·지터·RTT·프레임 드롭·끊김 — WebRTC `getStats()` 표준 통계
- 시청자 플레이어는 10초마다 품질 샘플을 서버에 보내고, **송출 스튜디오의 "시청 품질" 패널**에서 최근 5분 보고 시청자 수, 평균 비트레이트, RTT 평균/p95, 손실률, 끊김, 화질 선택 비율, 분당 추이를 볼 수 있습니다(방송 소유자만).

### 1-8. 채널 · 팔로우 · 방송 시작 알림
- 가입하면 **채널**(`/c/u{회원ID}`)이 생기고, 채널 페이지에서 주소·이름·소개를 바꿀 수 있습니다. 방송은 채널의 회차로 쌓입니다.
- 방송 화면·채널 페이지의 **+ 팔로우** → 상단 **팔로잉** 메뉴에서 팔로우한 채널을 방송 중인 순서로 확인
- 팔로우한 채널이 방송을 시작하면 **실시간 알림 토스트**가 뜹니다(SSE `/api/realtime/me`).

### 1-9. 채팅 관리 (매니저 · 채팅 금지/차단 · 슬로우 모드)
- 채널 소유자(👑)는 채팅의 **⋯** 메뉴에서 시청자를 **매니저(🛡)** 로 지정할 수 있습니다.
- 소유자·매니저는 남의 메시지 삭제, **1분/10분 채팅 금지, 영구 차단**, 채팅창 **⚙ 관리**에서 **슬로우 모드**(3~60초)와 제재 해제를 할 수 있습니다.
- 제재는 **채널 단위**라 다음 방송에도 유지되고, 제재되면 그 사용자의 메시지가 모든 시청자 화면에서 바로 숨겨집니다(SSE `moderation`).
- 서버에서 검사: 제재 중 → `403 CHAT_BANNED`, 슬로우 모드 간격 위반 → `429 SLOW_MODE`(관리자 예외), 매니저는 다른 매니저·소유자를 제재할 수 없음.

### 지원 환경
| 기능 | 지원 |
|---|---|
| PC 화면 공유 송출 | Chrome, Edge (최신). Firefox는 화면 소리 미지원 |
| 탭 소리 송출 | Chrome/Edge 탭 공유 |
| 시스템 소리 송출 | Windows Chrome/Edge 전체 화면 공유 (macOS는 OS 제한으로 탭 소리 사용) |
| 모바일 카메라 송출 | iOS Safari 15+, Android Chrome |
| 시청 | 위 브라우저 전부 (자동재생 정책상 소리는 버튼으로 켬) |

---

## 2. 아키텍처

```
[송출자 브라우저] ──WebRTC(영상·음성)──▶ [LiveKit Cloud SFU] ──WebRTC──▶ [시청자 브라우저 N명]
       │                                   ▲     │ webhook
       │ REST (인증·방송 제어·채팅)          │     ▼
       └──────────────────────────▶ [Spring Boot API] ──▶ [PostgreSQL]
                                      토큰 발급 / 방 생성·삭제 / 채팅 전달
```

- **미디어는 SFU(LiveKit Cloud)로 분리**: 송출자는 한 번만 업로드하고, 시청자 수만큼의 분배는 SFU가 담당합니다. TURN이 내장되어 모바일 데이터망에서도 연결됩니다.
- **권한은 서버가 결정**: 미디어 서버 접속에는 Spring 서버가 발급한 토큰이 반드시 필요합니다.
  - 송출 토큰: 로그인 + 방송 소유자 + 상태 검증 후 발급 (카메라·마이크·화면공유·화면공유 소리만 허용)
  - 시청 토큰: 비로그인도 발급, **송출·데이터 전송 권한 없음** → 미디어 서버 단에서도 송출 불가
- **API 서버는 Spring MVC + 가상 스레드(Java 21)**: 영상 트래픽이 API 서버를 거치지 않으므로 요청은 짧은 REST 뿐입니다. WebFlux 대신 MVC + JPA로 단순성과 안정성을 택했습니다.

### 방송 상태 관리
```
READY ──start──▶ LIVE ──end──▶ ENDED
                  │
                  ├─ 송출자 연결이 45초 이상 없음 → 자동 ENDED
                  └─ 미디어 서버의 방이 사라짐(webhook) → ENDED
```
- `start`: READY에서만 가능. LiveKit 방 생성 → LIVE → 송출 토큰 반환
- `end`: LIVE에서만 가능. **LiveKit 방을 삭제해 송출·시청 연결을 강제로 끊음**
- 방 삭제 실패 시에도 DB 상 종료는 확정하고, 스케줄러가 방 정리를 재시도합니다.
- 송출자 접속 여부는 **웹훅(participant_joined/left) + 15초 주기 조회**를 함께 사용해 웹훅 유실에도 상태가 어긋나지 않습니다.
- 동시에 들어온 시작/종료 요청은 JPA `@Version` 낙관적 락으로 하나만 성공합니다(나머지 `409`).
- 새로고침·네트워크 끊김 시 소유자는 `publisher-token`으로 같은 방송에 재접속할 수 있습니다.

### 인증
- 세션 기반(Spring Security) + **spring-session-jdbc**: 세션을 DB에 저장해 서버 재시작 후에도 로그인 유지
- 로그인 시 세션 ID 재발급(세션 고정 공격 방지), BCrypt 비밀번호 해시
- CSRF: `XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더 (웹훅은 LiveKit 서명 JWT로 별도 검증)
- 쿠키: `HttpOnly`, `SameSite=Lax`, 운영 환경 `Secure`

### 도네이션: 결제 검증과 중복 적립 방지
```
[충전 / 충전하고 바로 후원]
브라우저 ─▶ 서버: 충전 주문 저장 (orderId·금액·[바로 후원이면 후원 대상 방송]) = PENDING
         └▶ 토스 결제창(테스트) ─▶ successUrl?paymentKey&orderId&amount
서버 confirm: 주문 행 잠금(SELECT … FOR UPDATE)
   ├ 요청 금액 ≠ 주문 금액 → 결제사에 승인 요청 없이 거절
   ├ 토스 승인 API (Idempotency-Key) → 응답 status=DONE·totalAmount·orderId 재검증
   └ PENDING→DONE (1회만) → 지갑 충전 → [바로 후원] 저장된 후원 대상에게 포인트 이동
토스 웹훅: 본문은 믿지 않고 결제 조회 API로 재확인 → 같은 확정 로직

[포인트 후원]
서버: 후원 대상 = 방송 소유자 확인(본인 방송 불가) → 두 지갑을 사용자 ID 순서로 잠금
   → 잔액 확인 → 보낸 사람 −, 받는 사람 + , 원장 2줄 기록 → 커밋 후 실시간 알림
```
- **같은 승인 통지가 반복돼도 한 번만 적립**: 충전 주문 비관적 잠금 + PENDING→DONE 1회 전이 + `paymentKey` unique. 바로 후원은 `chargeOrderId` unique로 1회만 생성됩니다.
- **같은 후원 요청 중복 방지**: 클라이언트가 만든 `requestId`(unique)로 더블 클릭·재전송을 한 번만 처리합니다.
- **잔액 정합성**: 지갑 행 잠금 + 잔액 부족 시 예외로 후원 기록까지 롤백, 모든 변경은 `point_transactions` 원장에 잔액과 함께 기록됩니다.
- 결제 후 방송이 삭제된 경우 후원은 건너뛰고 충전된 포인트는 지갑에 남깁니다(결제 금액 유실 없음).

### 미디어 평면과 이벤트 평면 분리
```
미디어 평면: 송출자 ──WebRTC──▶ LiveKit SFU ──WebRTC──▶ 시청자          (영상·음성)
이벤트 평면: Spring ──SSE(/api/realtime/...)──▶ 브라우저                (채팅·후원·관리·알림)
```
- 채팅·후원 등 앱 이벤트는 `RealtimePublisher` 인터페이스로 보내고, 현재 구현은 **SSE(Server-Sent Events)** 입니다.
  - 방송이 꺼져 있어도(종료된 방송의 댓글, 앞으로의 텍스트 채널·개인 알림) 같은 방식으로 전달됩니다.
  - 서버를 여러 대로 늘리면 Redis Pub/Sub 구현으로 교체할 수 있게 인터페이스로 분리했습니다.
- 이벤트는 **DB 커밋 후**(`@TransactionalEventListener`) 발행되어, 롤백된 채팅·후원이 화면에 나가지 않습니다.
- 가상 스레드라 SSE 연결이 플랫폼 스레드를 점유하지 않고, 20초 하트비트로 프록시 유휴 끊김과 죽은 연결을 정리합니다.
- 클라이언트는 같은 방송의 구독을 EventSource 하나로 공유하고, 끊기면 자동 재연결합니다.

## 3. API 요약

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup` · `/login` · `/logout` | - | 회원가입·로그인·로그아웃 |
| POST | `/api/auth/demo-login?account=STREAMER\|STREAMER2\|VIEWER` | - | 평가용 테스트 계정 로그인 |
| GET | `/api/auth/me` | 로그인 | 내 정보 |
| GET | `/api/broadcasts?status=&page=` | 누구나 | 방송 목록 (LIVE 우선) |
| GET | `/api/broadcasts/{id}` | 누구나 | 방송 상세 |
| POST | `/api/broadcasts` | 로그인 | 방송 등록 |
| PATCH / DELETE | `/api/broadcasts/{id}` | 소유자 | 수정 / 삭제(방송 중이면 종료 후 삭제) |
| POST | `/api/broadcasts/{id}/start` | 소유자 | 방송 시작, 송출 토큰 |
| POST | `/api/broadcasts/{id}/publisher-token` | 소유자 | 재접속용 송출 토큰 |
| POST | `/api/broadcasts/{id}/end` | 소유자 | 방송 종료(송출 중단) |
| GET | `/api/broadcasts/{id}/viewer-token` | 누구나 | 시청 전용 토큰 |
| GET / POST | `/api/broadcasts/{id}/chats` | 조회 누구나 / 작성 로그인 | 채팅 |
| PATCH / DELETE | `/api/chats/{id}` | 작성자 (삭제는 소유자·매니저도) | 채팅 수정 / 삭제 |
| GET | `/api/wallet` | 로그인 | 포인트 잔액·내역 |
| POST | `/api/wallet/charges` | 로그인 | 충전 주문 생성 (`donation` 포함 시 충전하고 바로 후원) |
| POST | `/api/wallet/charges/confirm` | 주문자 | 결제 승인 검증 → 충전(→ 후원) |
| POST | `/api/broadcasts/{id}/donations` | 로그인(소유자 제외) | 포인트 후원 (`requestId` 멱등) |
| GET | `/api/broadcasts/{id}/donations` | 누구나 | 누적 후원 금액·건수·최근 내역 |
| POST | `/api/webhooks/toss` | 결제사 재조회로 검증 | 결제 상태 변경 통지 |
| POST | `/api/broadcasts/{id}/qoe` | 누구나(시청자, 5초 간격 제한) | 시청 품질 샘플 |
| GET | `/api/broadcasts/{id}/qoe?minutes=5` | 소유자 | 시청 품질 요약·추이 |
| GET | `/api/recordings`, `/api/recordings/{id}` | 누구나 | 녹화 영상 |
| GET | `/api/channels/{handle}`, `/api/channels/{handle}/broadcasts` | 누구나 | 채널 정보(팔로워 수·방송 중)·방송 회차 |
| GET / PATCH | `/api/me/channel` | 로그인 | 내 채널 조회·수정 |
| POST / DELETE | `/api/channels/{handle}/follow` | 로그인 | 팔로우·언팔로우 (멱등) |
| GET | `/api/me/following` | 로그인 | 팔로잉 목록 (방송 중 먼저) |
| GET | `/api/broadcasts/{id}/moderation` | 누구나(목록은 관리자만) | 슬로우 모드·관리 권한·매니저·제재 목록 |
| POST / DELETE | `/api/broadcasts/{id}/moderation/bans[/{userId}]` | 소유자·매니저 | 채팅 금지(분)·영구 차단 / 해제 |
| PUT | `/api/broadcasts/{id}/moderation/slow-mode` | 소유자·매니저 | 슬로우 모드(초) |
| PUT / DELETE | `/api/broadcasts/{id}/moderation/managers/{userId}` | 소유자 | 매니저 지정·해제 |
| GET (SSE) | `/api/realtime/broadcasts/{id}` | 누구나 | 방송 이벤트 구독 (chat, donation) |
| GET (SSE) | `/api/realtime/me` | 로그인 | 내 알림 구독 |
| POST | `/api/webhooks/livekit` | LiveKit 서명 | 미디어 서버 이벤트 |

에러 응답 형식: `{ "code": "NOT_BROADCAST_OWNER", "message": "방송 소유자만 할 수 있습니다.", "fieldErrors": {} }`

---

## 4. 실행 방법

### 기술 스택
- Backend: Java 21, Spring Boot 3.5 (Web MVC, Security, Data JPA, Validation), spring-session-jdbc, PostgreSQL / H2(로컬)
- Media: LiveKit Cloud (WebRTC SFU), `io.livekit:livekit-server`(Java SDK), `livekit-client`(JS SDK)
- Frontend: React 19, TypeScript, Vite, React Router
- Deploy: Docker, Render(Web Service Free), Neon(PostgreSQL Free)

### 환경변수 (`.env.example` 참고)
| 변수 | 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL. 비우면 로컬 H2 파일 DB |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit Cloud 프로젝트 키 |
| `COOKIE_SECURE` | HTTPS 환경에서 `true` |
| `PUBLISHER_GRACE_PERIOD` | 송출자 연결 끊김 후 자동 종료까지 (기본 `45s`) |
| `DEMO_ACCOUNTS_ENABLED` | 테스트 계정 버튼 (기본 `true`) |
| `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` | 토스페이먼츠 **테스트** 키 (`test_ck_…`, `test_sk_…`) |
| `RECORDING_ENABLED`, `S3_*` | 녹화 저장소(Cloudflare R2 등 S3 호환). 기본 비활성 |

### 로컬 실행
```bash
# 1) 백엔드 (Java 21)
cd backend
cp ../.env.example .env   # LiveKit 키 입력, DB 줄은 지우면 H2 사용
set -a; source .env; set +a
./gradlew bootRun         # http://localhost:8080

# 2) 프론트엔드 (Node 20+)
cd frontend
npm install
npm run dev               # http://localhost:5173 (/api → 8080 프록시)
```
> 카메라·화면 공유는 `localhost` 또는 HTTPS에서만 동작합니다. 휴대폰 테스트는 배포 주소를 이용하세요.

### Docker
```bash
cp .env.example .env      # 값 입력
docker compose up --build # http://localhost:8080 (PostgreSQL 포함)
```

### 테스트
```bash
cd backend && ./gradlew test
```
미디어 서버를 가짜 구현(`FakeMediaServer`)으로 바꿔 끼워 다음을 검증합니다.
- 비로그인 / 소유자 / 다른 사용자별 허용·거절 (방송 CRUD, 송출 시작·종료, 시청 토큰)
- 상태 전이(중복 시작·종료 409), 방송 중 삭제 시 송출 중단, 송출자 부재 자동 종료, 방 정리 재시도
- 채팅 작성자 권한·도배 방지, 웹훅 서명 검증, CSRF
- 지갑·도네이션: 반복 승인·반복 웹훅 시 1회 충전, 금액 조작 거절, 타인 주문 승인 거절, 승인 실패 기록, 포인트 이동·잔액 부족 롤백, `requestId` 중복 후원 방지, 충전하고 바로 후원 1회 처리, 결제 중 방송 삭제 시 포인트 보존

GitHub Actions(`.github/workflows/ci.yml`)가 push마다 백엔드 테스트와 프론트 린트·빌드를 실행합니다.

### 배포 (Render)
1. Neon에서 PostgreSQL 생성 → `jdbc:postgresql://<host>/<db>?sslmode=require`
2. Render → New → **Blueprint** → 이 저장소 선택 (`render.yaml`, Singapore 리전 — Neon DB와 같은 지역)
3. 환경변수 입력 → 배포
   - 필수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
     (`REQUIRE_PRODUCTION_CONFIG=true`라 하나라도 없으면 기동 실패 → 이전 버전 유지)
   - 결제: `TOSS_CLIENT_KEY`(`test_ck_…`), `TOSS_SECRET_KEY`(`test_sk_…`)
4. (선택) LiveKit Cloud → Settings → Webhooks에 `https://<서비스>/api/webhooks/livekit` 등록
5. (선택) 녹화: S3 호환 저장소를 만들고 `RECORDING_ENABLED=true`, `S3_*` 입력
   - 예) **Cloudflare R2**(현재 배포): 버킷 공개 주소(r2.dev) + API 토큰, `S3_REGION=auto`
     - hls.js 가 재생 목록·세그먼트를 직접 받으므로 버킷 **CORS에 서비스 도메인 GET 허용** 필요
   - 예) **Supabase Storage**(무료, 카드 불필요): 공개 버킷 생성 → Project Settings → Storage → S3 Access Keys
     - `S3_ENDPOINT=https://<project>.supabase.co/storage/v1/s3`, `S3_REGION=<프로젝트 리전, 예: ap-southeast-1>`
     - `S3_PUBLIC_BASE_URL=https://<project>.supabase.co/storage/v1/object/public/<버킷>`
   - HLS 세그먼트로 나눠 저장하므로 무료 저장소의 파일당 크기 제한에 걸리지 않습니다.

---

## 5. 프로젝트 구조
```
backend/src/main/java/com/live
├── user/        회원가입·로그인, 데모 계정
├── broadcast/   방송 엔티티·상태머신, 서비스, 상태 동기화 스케줄러
├── media/       MediaServer 추상화, LiveKit 구현, 웹훅
├── chat/        채팅 저장·권한, 커밋 후 실시간 전달
├── wallet/      포인트 지갑·원장, 충전 주문·승인 검증·멱등 처리
├── donation/    포인트 후원(대상 검증·지갑 간 이동), 실시간 알림
├── payment/     결제사 추상화(PaymentGateway), 토스페이먼츠 구현
├── recording/   HLS 녹화(Egress)·다시보기, 상태 조회 확정
├── qoe/         시청 품질 샘플 수집·요약(p95 등)
├── realtime/    이벤트 평면: RealtimePublisher, SSE 허브
├── channel/     채널·팔로우·방송 시작 알림, 채팅 관리(매니저·제재·슬로우 모드)
├── config/      Security, SPA 라우팅
└── common/      에러 코드·예외 처리
frontend/src
├── pages/       목록, 상세(시청), 스튜디오(송출), 채널·팔로잉, 지갑, 다시보기, 인증
├── components/  LivePlayer(화질·통계·볼륨·전체화면·재연결), VodPlayer(HLS), ChatPanel(관리), DonationBox, QoePanel
└── lib/         API 클라이언트(CSRF), 송출 세션(페이지 이동 중 유지), SSE 구독, QoE 측정, 지갑·인증 컨텍스트
```

## 6. 출처 및 사용 라이브러리
- [토스페이먼츠](https://docs.tosspayments.com) — 결제창 SDK v2, 코어 API(승인·조회) 테스트 모드
- [LiveKit](https://livekit.io) — WebRTC SFU(Cloud), `livekit-client`, `livekit-server` SDK (Apache-2.0)
- Spring Boot, Spring Security, Spring Session (Apache-2.0)
- React, Vite, React Router (MIT)
- 프론트엔드 초기 구조는 `npm create vite` React-TS 템플릿에서 시작했습니다.
- 개발 과정에서 AI 개발 도구(Claude)를 활용했습니다. 아래 [AI 활용 방식](#7-ai-활용-방식) 참고.

## 7. AI 활용 방식
공모 안내에 따라 AI 개발 도구를 적극 활용했고, 역할을 다음과 같이 나눴습니다.

| AI(Claude)가 맡은 것 | 직접 한 것 |
|---|---|
| 코드 초안 작성(백엔드·프론트), 테스트 초안, 문서 초안 | 기술 스택·아키텍처 방향 결정(SFU 분리, MVC, 포인트 지갑 + 바로 후원 혼합 구조 등) |
| 라이브러리 API 조사, 설계 대안 제시 | 실기기(PC·휴대폰) 송출·시청 검증, 배포 환경(Render·Neon·LiveKit·토스·R2) 구성 |
| 코드 리뷰·반례 제시 | 문제 발견과 우선순위 판단(채팅 체감 지연, 송출 중 페이지 이동 시 끊김, 리전 지연 등), 모든 PR 검토 후 머지 |

- 모든 변경은 이슈 → 브랜치 → PR → CI(테스트) 통과 → 머지 순서로 진행했고, 커밋에 AI 공동 작성 표기를 남겼습니다.
