package com.micropay.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micropay.backend.AbstractIntegrationTest;
import com.micropay.backend.application.services.WalletService;
import com.micropay.backend.domain.ports.out.WalletRepository;
import com.micropay.backend.domain.valueobjects.Currency;
import com.micropay.backend.domain.valueobjects.Money;
import com.micropay.backend.domain.valueobjects.WalletId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WalletService wallets;
    @Autowired WalletRepository walletRepository;

    @Test
    void authenticated_user_flow_preserves_balances_and_rejects_invalid_transfers() throws Exception {
        String aliceToken = registerAndLogin("alice");
        String bobToken = registerAndLogin("bob");
        WalletId alice = createWallet(aliceToken);
        WalletId bob = createWallet(bobToken);
        var source = wallets.getByIdOrThrow(alice);
        source.credit(Money.of(new BigDecimal("10000"), Currency.USD), "test funding");
        walletRepository.save(source);

        mvc.perform(get("/api/v1/wallets/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(10000));

        Map<String, Object> request = Map.of("wallet_from_id", alice.uuid(),
                "wallet_to_id", bob.uuid(), "amount", 3000, "currency", "USD", "note", "API regression");
        String response = transfer(aliceToken, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.account_moves.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String transactionId = json.readTree(response).path("id").asText();
        mvc.perform(get("/api/v1/transactions/" + transactionId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_moves.length()").value(2));
        mvc.perform(get("/api/v1/transactions").param("wallet_id", alice.uuid().toString())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(transactionId));

        transfer(bobToken, request).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code")
                        .value("BUSINESS_RULE_VIOLATION_WALLET_NOT_OWNED_BY_USER"));
        transfer(aliceToken, Map.of("wallet_from_id", alice.uuid(), "wallet_to_id", bob.uuid(),
                        "amount", 8000, "currency", "USD"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code")
                        .value("BUSINESS_RULE_VIOLATION_INSUFFICIENT_AVAILABLE_BALANCE"));
        wallets.freeze(bob);
        transfer(aliceToken, request).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("BUSINESS_RULE_VIOLATION_WALLET_NOT_ACTIVE"));
        wallets.activate(bob);

        // Occupy the second lock: failed acquisition must release only the first.
        WalletId second = alice.uuid().compareTo(bob.uuid()) < 0 ? bob : alice;
        WalletId first = second.equals(bob) ? alice : bob;
        String occupiedKey = "lock:wallet:" + second.uuid();
        redis.opsForValue().set(occupiedKey, "another-owner", Duration.ofSeconds(30));
        try {
            transfer(aliceToken, request).andExpect(status().isConflict())
                    .andExpect(header().string("Retry-After", "2"))
                    .andExpect(header().string("X-Micropay-Attempts", "3"));
            assertThat(redis.opsForValue().get(occupiedKey)).isEqualTo("another-owner");
            assertThat(redis.hasKey("lock:wallet:" + first.uuid())).isFalse();
        } finally {
            redis.delete(occupiedKey);
        }
        assertThat(wallets.getByIdOrThrow(alice).balance()).isEqualTo(Money.of(new BigDecimal("7000"), Currency.USD));
        assertThat(wallets.getByIdOrThrow(bob).balance()).isEqualTo(Money.of(new BigDecimal("3000"), Currency.USD));
        assertThat(testJdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isEqualTo(1);
        assertThat(testJdbc.queryForObject("SELECT count(*) FROM account_moves", Integer.class)).isEqualTo(2);
        mvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    private String registerAndLogin(String name) throws Exception {
        String email = name + "+" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "StrongTest123!",
                                "full_name", name + " Integration"))))
                .andExpect(status().isCreated());
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "StrongTest123!"))))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode login = json.readTree(response);
        return login.path("access_token").asText();
    }

    private WalletId createWallet(String token) throws Exception {
        String response = mvc.perform(post("/api/v1/wallets").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return WalletId.from(UUID.fromString(json.readTree(response).path("id").asText()));
    }

    private ResultActions transfer(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
