package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.live.broadcast.BroadcastRepository;
import com.live.broadcast.BroadcastService;
import com.live.broadcast.BroadcastStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BroadcastAuthorizationTest extends IntegrationTestSupport {
    @Autowired BroadcastService broadcastService;
    @Autowired BroadcastRepository broadcastRepository;

    @Nested
    @DisplayName("비로그인 사용자")
    class Anonymous {
        @Test
        void canListAndViewDetail() throws Exception {
            long id = createBroadcast(signup("owner"), "공개 방송");
            perform(get("/api/broadcasts"), null, null).andExpect(status().isOk());
            perform(get("/api/broadcasts/" + id), null, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("READY"));
        }

        @Test
        void canWatchLiveWithViewOnlyToken() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "라이브");
            start(owner, id);
            perform(get("/api/broadcasts/" + id + "/viewer-token"), null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("viewer"))
                    .andExpect(jsonPath("$.token", startsWith("view:")));
        }

        @Test
        void cannotCreateOrControl() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "방송");
            perform(post("/api/broadcasts"), null, "{\"title\":\"x\"}").andExpect(status().isUnauthorized());
            perform(post("/api/broadcasts/" + id + "/start"), null, "{\"source\":\"SCREEN\"}").andExpect(status().isUnauthorized());
            perform(patch("/api/broadcasts/" + id), null, "{\"title\":\"x\"}").andExpect(status().isUnauthorized());
            perform(delete("/api/broadcasts/" + id), null, null).andExpect(status().isUnauthorized());
            perform(post("/api/broadcasts/" + id + "/end"), null, null).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("다른 사용자")
    class OtherUser {
        @Test
        void cannotModifyOrControlSomeoneElsesBroadcast() throws Exception {
            MockHttpSession owner = signup("owner");
            MockHttpSession other = signup("other");
            long id = createBroadcast(owner, "남의 방송");

            perform(patch("/api/broadcasts/" + id), other, "{\"title\":\"탈취\"}").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_BROADCAST_OWNER"));
            perform(post("/api/broadcasts/" + id + "/start"), other, "{\"source\":\"SCREEN\"}").andExpect(status().isForbidden());
            perform(delete("/api/broadcasts/" + id), other, null).andExpect(status().isForbidden());

            start(owner, id);
            perform(post("/api/broadcasts/" + id + "/publisher-token"), other, null).andExpect(status().isForbidden());
            perform(post("/api/broadcasts/" + id + "/end"), other, null).andExpect(status().isForbidden());
            assertThat(media.rooms).hasSize(1); // 남이 종료하지 못함
        }

        @Test
        void canWatch() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "라이브");
            start(owner, id);
            perform(get("/api/broadcasts/" + id + "/viewer-token"), signup("viewer"), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("viewer"));
        }
    }

    @Nested
    @DisplayName("방송 소유자")
    class Owner {
        @Test
        void fullLifecycle() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "처음 제목");

            perform(patch("/api/broadcasts/" + id), owner, "{\"title\":\"바뀐 제목\",\"description\":\"설명\"}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("바뀐 제목"));

            JsonNode conn = start(owner, id);
            assertThat(conn.get("role").asText()).isEqualTo("publisher");
            assertThat(conn.get("token").asText()).startsWith("pub:");
            String room = conn.get("roomName").asText();
            assertThat(media.rooms).contains(room);

            // 이미 방송 중이면 다시 시작할 수 없다
            perform(post("/api/broadcasts/" + id + "/start"), owner, "{\"source\":\"SCREEN\"}").andExpect(status().isConflict());
            // 새로고침 후 재접속용 토큰
            perform(post("/api/broadcasts/" + id + "/publisher-token"), owner, null).andExpect(status().isOk());

            perform(post("/api/broadcasts/" + id + "/end"), owner, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"));
            assertThat(media.rooms).doesNotContain(room); // 송출 중단

            perform(post("/api/broadcasts/" + id + "/end"), owner, null).andExpect(status().isConflict());
            perform(post("/api/broadcasts/" + id + "/start"), owner, "{\"source\":\"SCREEN\"}").andExpect(status().isConflict());
            perform(get("/api/broadcasts/" + id + "/viewer-token"), null, null).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("방송 중 삭제하면 송출을 중단하고 목록에서 사라진다")
        void deleteWhileLive() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "삭제할 방송");
            String room = start(owner, id).get("roomName").asText();

            perform(delete("/api/broadcasts/" + id), owner, null).andExpect(status().isNoContent());
            assertThat(media.rooms).doesNotContain(room);
            perform(get("/api/broadcasts/" + id), null, null).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("종료된 방송은 기본 목록에서 빠지고 종료 목록에서만 보인다")
        void endedBroadcastLeavesDefaultList() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "끝난 방송");
            start(owner, id);
            perform(post("/api/broadcasts/" + id + "/end"), owner, null).andExpect(status().isOk());

            String def = perform(get("/api/broadcasts?size=50"), null, null).andReturn().getResponse().getContentAsString();
            String ended = perform(get("/api/broadcasts?status=ENDED&size=50"), null, null).andReturn().getResponse().getContentAsString();
            assertThat(om.readTree(def).get("items")).noneMatch(b -> b.get("id").asLong() == id);
            assertThat(om.readTree(ended).get("items")).anyMatch(b -> b.get("id").asLong() == id);
            perform(get("/api/broadcasts/" + id), null, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"));
        }

        @Test
        void viewerTokenNotIssuedBeforeStart() throws Exception {
            long id = createBroadcast(signup("owner"), "대기 방송");
            perform(get("/api/broadcasts/" + id + "/viewer-token"), null, null)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
        }
    }

    @Nested
    @DisplayName("방송 상태 관리")
    class StateManagement {
        @Test
        @DisplayName("송출자가 유예 시간 넘게 없으면 자동 종료")
        void autoEndWhenPublisherGone() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "끊긴 방송");
            String room = start(owner, id).get("roomName").asText();

            broadcastService.reconcilePresence(id, Duration.ofSeconds(-1));

            assertThat(broadcastRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.ENDED);
            assertThat(media.rooms).doesNotContain(room);
        }

        @Test
        void keepLiveWhilePublisherPresent() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "정상 방송");
            JsonNode conn = start(owner, id);
            media.participants.put(conn.get("roomName").asText(), Set.of(conn.get("identity").asText()));

            broadcastService.reconcilePresence(id, Duration.ofSeconds(-1));

            assertThat(broadcastRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.LIVE);
        }

        @Test
        @DisplayName("방 정리에 실패해도 종료는 확정되고, 이후 재시도로 정리된다")
        void retryRoomCloseOnFailure() throws Exception {
            MockHttpSession owner = signup("owner");
            long id = createBroadcast(owner, "정리 실패");
            String room = start(owner, id).get("roomName").asText();

            media.failDelete = true;
            perform(post("/api/broadcasts/" + id + "/end"), owner, null).andExpect(status().isOk());
            assertThat(broadcastRepository.findById(id).orElseThrow().isRoomClosed()).isFalse();

            media.failDelete = false;
            broadcastService.retryCloseRoom(id);
            assertThat(broadcastRepository.findById(id).orElseThrow().isRoomClosed()).isTrue();
            assertThat(media.rooms).doesNotContain(room);
        }
    }
}
