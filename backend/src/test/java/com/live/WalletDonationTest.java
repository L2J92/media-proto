package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletDonationTest extends IntegrationTestSupport {

    // ---------- helpers ----------

    private JsonNode prepareCharge(MockHttpSession user, long amount, Long donateTo) throws Exception {
        String body = donateTo == null
                ? "{\"amount\":" + amount + "}"
                : "{\"amount\":" + amount + ",\"donation\":{\"broadcastId\":" + donateTo + ",\"message\":\"화이팅\"}}";
        return om.readTree(perform(post("/api/wallet/charges"), user, body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String confirmBody(String paymentKey, String orderId, long amount) {
        return "{\"paymentKey\":\"" + paymentKey + "\",\"orderId\":\"" + orderId + "\",\"amount\":" + amount + "}";
    }

    /** 결제창 결제 + 서버 승인까지 마친 충전 */
    private void charge(MockHttpSession user, long amount) throws Exception {
        String orderId = prepareCharge(user, amount, null).get("orderId").asText();
        String key = "pay_" + UUID.randomUUID();
        payments.userPaid(key, orderId, amount);
        perform(post("/api/wallet/charges/confirm"), user, confirmBody(key, orderId, amount)).andExpect(status().isOk());
    }

    private long balance(MockHttpSession user) throws Exception {
        return om.readTree(perform(get("/api/wallet"), user, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("balance").asLong();
    }

    private JsonNode summary(long broadcastId) throws Exception {
        return om.readTree(perform(get("/api/broadcasts/" + broadcastId + "/donations"), null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String donateBody(long amount, String requestId) {
        return "{\"amount\":" + amount + ",\"message\":\"응원\",\"requestId\":\"" + requestId + "\"}";
    }

    private void hook(String paymentKey, String orderId) throws Exception {
        mvc.perform(post("/api/webhooks/toss").contentType("application/json")
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"" + paymentKey
                                + "\",\"orderId\":\"" + orderId + "\",\"status\":\"DONE\"}}"))
                .andExpect(status().isOk());
    }

    // ---------- 충전 ----------

    @Nested
    @DisplayName("포인트 충전")
    class Charge {
        @Test
        @DisplayName("승인 요청·웹훅이 반복돼도 한 번만 충전")
        void idempotent() throws Exception {
            MockHttpSession user = signup("charger");
            String orderId = prepareCharge(user, 10000, null).get("orderId").asText();
            payments.userPaid("pay_c", orderId, 10000);

            perform(post("/api/wallet/charges/confirm"), user, confirmBody("pay_c", orderId, 10000))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(10000));
            perform(post("/api/wallet/charges/confirm"), user, confirmBody("pay_c", orderId, 10000))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(10000));
            hook("pay_c", orderId);
            hook("pay_c", orderId);

            assertThat(balance(user)).isEqualTo(10000);
            assertThat(payments.confirmCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("웹훅만 와도 결제사 재조회로 확정, 반복 통지는 1회 충전")
        void webhookOnly() throws Exception {
            MockHttpSession user = signup("charger");
            String orderId = prepareCharge(user, 3000, null).get("orderId").asText();
            payments.userPaid("pay_w", orderId, 3000);
            payments.approveDirectly("pay_w");
            for (int i = 0; i < 3; i++) hook("pay_w", orderId);
            assertThat(balance(user)).isEqualTo(3000);
        }

        @Test
        @DisplayName("브라우저가 금액을 바꿔 보내면 결제사에 승인 요청하지 않는다")
        void amountTampering() throws Exception {
            MockHttpSession user = signup("charger");
            String orderId = prepareCharge(user, 10000, null).get("orderId").asText();
            payments.userPaid("pay_t", orderId, 1000);
            perform(post("/api/wallet/charges/confirm"), user, confirmBody("pay_t", orderId, 1000))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
            assertThat(payments.confirmCalls.get()).isZero();
            assertThat(balance(user)).isZero();
        }

        @Test
        void otherUserCannotConfirm() throws Exception {
            MockHttpSession user = signup("charger");
            String orderId = prepareCharge(user, 1000, null).get("orderId").asText();
            payments.userPaid("pay_o", orderId, 1000);
            perform(post("/api/wallet/charges/confirm"), signup("thief"), confirmBody("pay_o", orderId, 1000))
                    .andExpect(status().isForbidden());
        }

        @Test
        void rejectedPaymentIsRecordedAsFailed() throws Exception {
            MockHttpSession user = signup("charger");
            String orderId = prepareCharge(user, 1000, null).get("orderId").asText();
            perform(post("/api/wallet/charges/confirm"), user, confirmBody("pay_none", orderId, 1000))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
            payments.userPaid("pay_late", orderId, 1000);
            perform(post("/api/wallet/charges/confirm"), user, confirmBody("pay_late", orderId, 1000))
                    .andExpect(status().isConflict());
            assertThat(balance(user)).isZero();
        }

        @Test
        void validation() throws Exception {
            perform(post("/api/wallet/charges"), null, "{\"amount\":1000}").andExpect(status().isUnauthorized());
            perform(post("/api/wallet/charges"), signup("charger"), "{\"amount\":500}").andExpect(status().isBadRequest());
        }
    }

    // ---------- 포인트 후원 ----------

    @Nested
    @DisplayName("포인트 후원")
    class Donate {
        @Test
        @DisplayName("포인트가 보낸 사람에서 방송 소유자에게 이동하고, 같은 requestId 는 한 번만 처리")
        void transferAndIdempotent() throws Exception {
            MockHttpSession streamer = signup("streamer");
            MockHttpSession donor = signup("donor");
            long id = createBroadcast(streamer, "후원 방송");
            start(streamer, id);
            charge(donor, 10000);

            perform(post("/api/broadcasts/" + id + "/donations"), donor, donateBody(3000, "req-1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(7000));
            perform(post("/api/broadcasts/" + id + "/donations"), donor, donateBody(3000, "req-1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(7000));

            assertThat(balance(donor)).isEqualTo(7000);
            assertThat(balance(streamer)).isEqualTo(3000);
            assertThat(summary(id).get("totalAmount").asLong()).isEqualTo(3000);
            assertThat(summary(id).get("count").asLong()).isEqualTo(1);
            assertThat(realtime.published).anyMatch(s -> s.startsWith("broadcast:" + id + "|donation|"));
        }

        @Test
        @DisplayName("잔액이 부족하면 409, 아무것도 바뀌지 않는다")
        void insufficient() throws Exception {
            MockHttpSession streamer = signup("streamer");
            MockHttpSession donor = signup("donor");
            long id = createBroadcast(streamer, "후원 방송");
            charge(donor, 1000);

            perform(post("/api/broadcasts/" + id + "/donations"), donor, donateBody(5000, "req-x"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
            assertThat(balance(donor)).isEqualTo(1000);
            assertThat(balance(streamer)).isZero();
            assertThat(summary(id).get("count").asLong()).isZero();
        }

        @Test
        void validation() throws Exception {
            MockHttpSession streamer = signup("streamer");
            long id = createBroadcast(streamer, "후원 방송");
            perform(post("/api/broadcasts/" + id + "/donations"), null, donateBody(1000, "a")).andExpect(status().isUnauthorized());
            perform(post("/api/broadcasts/" + id + "/donations"), signup("donor"), donateBody(500, "b")).andExpect(status().isBadRequest());
            perform(post("/api/broadcasts/" + id + "/donations"), streamer, donateBody(1000, "c"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_DONATE_TO_SELF"));
        }
    }

    // ---------- 충전하고 바로 후원 ----------

    @Nested
    @DisplayName("충전하고 바로 후원")
    class ChargeAndDonate {
        @Test
        @DisplayName("결제 승인 후 후원 대상·금액대로 후원, 반복 승인·웹훅에도 1회")
        void chargeThenDonateOnce() throws Exception {
            MockHttpSession streamer = signup("streamer");
            MockHttpSession donor = signup("donor");
            long id = createBroadcast(streamer, "후원 방송");
            start(streamer, id);

            JsonNode order = prepareCharge(donor, 5000, id);
            String orderId = order.get("orderId").asText();
            assertThat(order.get("orderName").asText()).contains("후원");
            payments.userPaid("pay_d", orderId, 5000);

            perform(post("/api/wallet/charges/confirm"), donor, confirmBody("pay_d", orderId, 5000))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.donation.amount").value(5000))
                    .andExpect(jsonPath("$.balance").value(0));
            perform(post("/api/wallet/charges/confirm"), donor, confirmBody("pay_d", orderId, 5000)).andExpect(status().isOk());
            hook("pay_d", orderId);

            assertThat(balance(donor)).isZero();
            assertThat(balance(streamer)).isEqualTo(5000);
            assertThat(summary(id).get("totalAmount").asLong()).isEqualTo(5000);
            assertThat(summary(id).get("count").asLong()).isEqualTo(1);
        }

        @Test
        void cannotTargetOwnBroadcast() throws Exception {
            MockHttpSession streamer = signup("streamer");
            long id = createBroadcast(streamer, "내 방송");
            perform(post("/api/wallet/charges"), streamer, "{\"amount\":1000,\"donation\":{\"broadcastId\":" + id + "}}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_DONATE_TO_SELF"));
        }

        @Test
        @DisplayName("결제 사이 방송이 삭제되면 포인트만 충전되고 후원은 건너뛴다")
        void broadcastDeletedMeanwhile() throws Exception {
            MockHttpSession streamer = signup("streamer");
            MockHttpSession donor = signup("donor");
            long id = createBroadcast(streamer, "곧 삭제");
            String orderId = prepareCharge(donor, 2000, id).get("orderId").asText();
            perform(delete("/api/broadcasts/" + id), streamer, null).andExpect(status().isNoContent());
            payments.userPaid("pay_del", orderId, 2000);

            perform(post("/api/wallet/charges/confirm"), donor, confirmBody("pay_del", orderId, 2000))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(2000));
            assertThat(balance(streamer)).isZero();
        }
    }
}
