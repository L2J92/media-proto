package com.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 운영과 같은 PostgreSQL 에서 마이그레이션·잠금·제약을 검증한다.
 * Docker 가 없는 로컬 환경에서는 건너뛰고, CI(GitHub Actions)에서는 실행된다.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest extends IntegrationTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway V1~V3 가 적용되고 엔티티 검증(validate)을 통과한다")
    void migrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).contains("1", "2", "3");
        assertThat(jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class))
                .isGreaterThanOrEqualTo(160000);
    }

    @Test
    @DisplayName("같은 충전 승인을 동시에 보내도 결제사 승인은 1번, 포인트도 1번만 충전된다")
    void concurrentConfirmChargesOnce() throws Exception {
        MockHttpSession user = signup("racer");
        String orderId = om.readTree(perform(post("/api/wallet/charges"), user, "{\"amount\":10000}")
                .andReturn().getResponse().getContentAsString()).get("orderId").asText();
        payments.userPaid("pay_race", orderId, 10000);
        String body = "{\"paymentKey\":\"pay_race\",\"orderId\":\"" + orderId + "\",\"amount\":10000}";

        List<Integer> statuses = runConcurrently(4, i ->
                perform(post("/api/wallet/charges/confirm"), user, body).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(payments.confirmCalls.get()).isEqualTo(1);
        assertThat(balance(user)).isEqualTo(10000);
    }

    @Test
    @DisplayName("잔액보다 많은 후원이 동시에 몰려도 잔액이 음수가 되지 않고 받은 금액과 정확히 맞는다")
    void concurrentDonationsNeverOverdraw() throws Exception {
        MockHttpSession streamer = signup("streamer");
        MockHttpSession donor = signup("donor");
        long id = createBroadcast(streamer, "동시 후원");
        String orderId = om.readTree(perform(post("/api/wallet/charges"), donor, "{\"amount\":5000}")
                .andReturn().getResponse().getContentAsString()).get("orderId").asText();
        payments.userPaid("pay_5k", orderId, 5000);
        perform(post("/api/wallet/charges/confirm"), donor,
                "{\"paymentKey\":\"pay_5k\",\"orderId\":\"" + orderId + "\",\"amount\":5000}");

        AtomicInteger ok = new AtomicInteger();
        List<Integer> statuses = runConcurrently(4, i -> {
            int s = perform(post("/api/broadcasts/" + id + "/donations"), donor,
                    "{\"amount\":2000,\"requestId\":\"" + UUID.randomUUID() + "\"}").andReturn().getResponse().getStatus();
            if (s == 200) ok.incrementAndGet();
            return s;
        });

        // 5,000P 로 2,000P 후원은 최대 2번
        assertThat(ok.get()).isEqualTo(2);
        assertThat(statuses).containsOnly(200, 409);
        assertThat(balance(donor)).isEqualTo(1000);
        assertThat(balance(streamer)).isEqualTo(4000);
        Long ledgerSum = jdbc.queryForObject("select coalesce(sum(amount),0) from point_transactions", Long.class);
        Long walletSum = jdbc.queryForObject("select coalesce(sum(balance),0) from wallets", Long.class);
        assertThat(ledgerSum).isEqualTo(walletSum); // 원장 합계 = 지갑 잔액 합계
    }

    private long balance(MockHttpSession user) throws Exception {
        return om.readTree(perform(get("/api/wallet"), user, null).andReturn().getResponse().getContentAsString())
                .get("balance").asLong();
    }

    interface Task { int run(int i) throws Exception; }

    /** 모든 스레드를 동시에 출발시켜 결과(HTTP 상태)를 모은다 */
    private List<Integer> runConcurrently(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            futures.add(pool.submit(() -> { start.await(); return task.run(n); }));
        }
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }
}
