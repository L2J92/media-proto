package com.live;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DonationTest extends IntegrationTestSupport {

    private JsonNode prepare(MockHttpSession donor, long broadcastId, long amount) throws Exception {
        String body = perform(post("/api/broadcasts/" + broadcastId + "/donations"), donor,
                "{\"amount\":" + amount + ",\"message\":\"화이팅\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private String confirmBody(String paymentKey, String orderId, long amount) {
        return "{\"paymentKey\":\"" + paymentKey + "\",\"orderId\":\"" + orderId + "\",\"amount\":" + amount + "}";
    }

    private JsonNode summary(long broadcastId) throws Exception {
        return om.readTree(perform(get("/api/broadcasts/" + broadcastId + "/donations"), null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("주문 → 결제 → 서버 승인 → 적립, 같은 승인 요청이 반복돼도 한 번만 적립")
    void confirmIsIdempotent() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "후원 방송");
        start(streamer, id);

        JsonNode order = prepare(donor, id, 5000);
        String orderId = order.get("orderId").asText();
        assertThat(order.get("amount").asLong()).isEqualTo(5000);
        assertThat(order.get("clientKey").asText()).isEqualTo("test_ck_fake");

        payments.userPaid("pay_1", orderId, 5000);
        perform(post("/api/donations/confirm"), donor, confirmBody("pay_1", orderId, 5000))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(5000));
        // 새로고침으로 같은 요청이 다시 와도 성공 응답, 적립은 그대로
        perform(post("/api/donations/confirm"), donor, confirmBody("pay_1", orderId, 5000)).andExpect(status().isOk());
        // 웹훅이 같은 결제를 또 통지
        mvc.perform(post("/api/webhooks/toss").contentType("application/json")
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"pay_1\",\"orderId\":\"" + orderId + "\",\"status\":\"DONE\"}}"))
                .andExpect(status().isOk());

        JsonNode s = summary(id);
        assertThat(s.get("totalAmount").asLong()).isEqualTo(5000);
        assertThat(s.get("count").asLong()).isEqualTo(1);
        assertThat(payments.confirmCalls.get()).isEqualTo(1);
        assertThat(media.sentData).anyMatch(d -> d.contains("|donation|"));
    }

    @Test
    @DisplayName("웹훅만 와도 결제사에 재조회해 확정하고, 반복 통지는 한 번만 적립")
    void webhookConfirmsOnce() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "후원 방송");
        String orderId = prepare(donor, id, 3000).get("orderId").asText();
        payments.userPaid("pay_w", orderId, 3000);
        payments.approveDirectly("pay_w");

        String hook = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"pay_w\",\"orderId\":\"" + orderId + "\",\"status\":\"DONE\"}}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/webhooks/toss").contentType("application/json").content(hook)).andExpect(status().isOk());
        }
        assertThat(summary(id).get("totalAmount").asLong()).isEqualTo(3000);
        assertThat(summary(id).get("count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("브라우저가 금액을 바꿔 보내면 결제사에 승인 요청하지 않는다")
    void amountTamperingRejected() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "후원 방송");
        String orderId = prepare(donor, id, 10000).get("orderId").asText();
        payments.userPaid("pay_t", orderId, 1000);

        perform(post("/api/donations/confirm"), donor, confirmBody("pay_t", orderId, 1000))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        assertThat(payments.confirmCalls.get()).isZero();
        assertThat(summary(id).get("totalAmount").asLong()).isZero();
    }

    @Test
    void otherUserCannotConfirmMyOrder() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "후원 방송");
        String orderId = prepare(donor, id, 1000).get("orderId").asText();
        payments.userPaid("pay_o", orderId, 1000);

        perform(post("/api/donations/confirm"), signup("thief"), confirmBody("pay_o", orderId, 1000))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectedPaymentIsRecordedAsFailed() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "후원 방송");
        String orderId = prepare(donor, id, 1000).get("orderId").asText();
        // 결제창에서 결제되지 않은 paymentKey
        perform(post("/api/donations/confirm"), donor, confirmBody("pay_none", orderId, 1000))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_FAILED"));
        // 실패한 주문은 다시 승인할 수 없다
        payments.userPaid("pay_late", orderId, 1000);
        perform(post("/api/donations/confirm"), donor, confirmBody("pay_late", orderId, 1000))
                .andExpect(status().isConflict());
    }

    @Test
    void validation() throws Exception {
        MockHttpSession streamer = signup("streamer");
        long id = createBroadcast(streamer, "후원 방송");
        perform(post("/api/broadcasts/" + id + "/donations"), null, "{\"amount\":1000}").andExpect(status().isUnauthorized());
        perform(post("/api/broadcasts/" + id + "/donations"), signup("donor2"), "{\"amount\":500}").andExpect(status().isBadRequest());
        perform(post("/api/broadcasts/" + id + "/donations"), streamer, "{\"amount\":1000}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_DONATE_TO_SELF"));
    }
}
