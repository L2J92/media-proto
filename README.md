# media-proto · 실시간 방송 플랫폼

PC 화면 공유(탭·시스템 소리 + 마이크)와 휴대폰 카메라로 라이브 방송을 송출하고, 다른 기기에서 실시간으로 시청하는 웹 서비스입니다.

| 항목 | 내용 |
|---|---|
| 서비스 주소 | **https://(배포 후 기입).onrender.com** |
| 시연 영상 | **(링크 기입)** — PC 송출·시청·종료 / 모바일 송출·시청·종료 |
| 테스트 계정 | 로그인 화면의 **[방송자 계정] [방송자2 계정] [시청자 계정]** 버튼으로 비밀번호 없이 바로 로그인 |

> 무료 호스팅(Render Free)을 사용합니다. 15분간 접속이 없으면 서버가 잠들어 **첫 접속 시 약 1분** 로딩될 수 있습니다.

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
5. **다른 기기(휴대폰 등)**에서 서비스 주소 접속 → 방송 목록의 `LIVE` 방송 클릭 → 영상 재생, **🔊 소리 켜기** 클릭 시 화면 소리 + 마이크 음성 재생
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
| 다른 사용자가 남의 채팅 수정·삭제 | `403 NOT_CHAT_AUTHOR` |
| 서버 재시작 후 | 방송 정보·채팅·로그인 세션 유지 (PostgreSQL 저장) |

### 1-4. 채팅 (선택 요구사항 · 댓글)
- 로그인 사용자는 방송별로 채팅을 작성하고, **본인 채팅만** 수정·삭제할 수 있습니다.
- 방송 중에는 다른 시청자 화면에 실시간으로 반영되고, 종료된 방송에서는 댓글처럼 조회·작성됩니다.

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

### 채팅 실시간 전달
채팅은 REST로 저장(작성자 권한 검증) → **DB 커밋 후** 서버가 LiveKit `sendData`로 방 전체에 전달합니다. 시청자 토큰에는 데이터 전송 권한이 없어 클라이언트가 임의로 메시지를 뿌릴 수 없습니다.

---

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
| PATCH / DELETE | `/api/chats/{id}` | 작성자 | 채팅 수정 / 삭제 |
| GET | `/api/recordings`, `/api/recordings/{id}` | 누구나 | 녹화 영상 |
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
- 채팅 작성자 권한, 웹훅 서명 검증, CSRF

### 배포 (Render)
1. Neon에서 PostgreSQL 생성 → `jdbc:postgresql://<host>/<db>?sslmode=require`
2. Render → New → **Blueprint** → 이 저장소 선택 (`render.yaml`)
3. 환경변수 6개(DB 3, LiveKit 3) 입력 → 배포
4. (선택) LiveKit Cloud → Settings → Webhooks에 `https://<서비스>/api/webhooks/livekit` 등록

---

## 5. 프로젝트 구조
```
backend/src/main/java/com/live
├── user/        회원가입·로그인, 데모 계정
├── broadcast/   방송 엔티티·상태머신, 서비스, 상태 동기화 스케줄러
├── media/       MediaServer 추상화, LiveKit 구현, 웹훅
├── chat/        채팅 저장·권한, 커밋 후 실시간 전달
├── recording/   녹화(Egress) 메타데이터
├── config/      Security, SPA 라우팅
└── common/      에러 코드·예외 처리
frontend/src
├── pages/       목록, 상세(시청), 스튜디오(송출), 인증, 다시보기
├── components/  LivePlayer, ChatPanel, Layout
└── lib/         API 클라이언트(CSRF), 인증 컨텍스트
```

## 6. 출처 및 사용 라이브러리
- [LiveKit](https://livekit.io) — WebRTC SFU(Cloud), `livekit-client`, `livekit-server` SDK (Apache-2.0)
- Spring Boot, Spring Security, Spring Session (Apache-2.0)
- React, Vite, React Router (MIT)
- 프론트엔드 초기 구조는 `npm create vite` React-TS 템플릿에서 시작했습니다.
- 개발 과정에서 AI 개발 도구(Claude)를 활용했습니다.
