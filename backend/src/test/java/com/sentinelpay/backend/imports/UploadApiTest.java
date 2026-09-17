package com.sentinelpay.backend.imports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(UploadApiTest.Config.class)
@ActiveProfiles("uploads")
class UploadApiTest {
    static HttpServer keys;
    static ECKey key;
    static String issuer;
    static EmbeddedPostgres postgres;
    static {
        try {
            key = new ECKeyGenerator(Curve.P_256).keyID("test").generate();
            keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            keys.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            keys.start(); issuer = "http://127.0.0.1:" + keys.getAddress().getPort() + "/auth/v1";
            postgres = EmbeddedPostgres.builder().start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @Configuration @EnableWebMvc @Import({UploadSecurity.class, UploadController.class, UploadErrors.class})
    static class Config {
        @Bean PrivateUploadStore store() {
            var source = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(source).locations("classpath:db/uploads").load().migrate();
            return new PrivateUploadStore(source);
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("uploads.supabase-url", () -> issuer.replace("/auth/v1", ""));
        registry.add("uploads.allowed-origin", () -> "https://example.com");
    }
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @AfterAll static void close() throws Exception { keys.stop(0); postgres.close(); }
    String token(UUID user, String audience, String tokenIssuer, Instant expiry, ECKey signingKey) {
        var claims = JwtClaimsSet.builder().issuer(tokenIssuer).subject(user.toString()).audience(List.of(audience))
                .issuedAt(expiry.minusSeconds(600)).expiresAt(expiry).claim("role", "authenticated").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).keyId("test").build(), claims)).getTokenValue();
    }
    String token(UUID user) { return token(user,"authenticated",issuer,Instant.now().plusSeconds(300),key); }
    MockMultipartFile csv(String content) { return new MockMultipartFile("file","payments.csv","text/csv",content.getBytes(StandardCharsets.UTF_8)); }
    @Test void rejectsMissingExpiredWrongAudienceWrongIssuerAndForgedTokens() throws Exception {
        mvc.perform(get("/api/uploads")).andExpect(status().isUnauthorized());
        for (String bad : List.of(token(alice,"wrong",issuer,Instant.now().plusSeconds(300),key),
                token(alice,"authenticated","https://wrong.example/auth/v1",Instant.now().plusSeconds(300),key),
                token(alice,"authenticated",issuer,Instant.now().minusSeconds(120),key),
                token(alice,"authenticated",issuer,Instant.now().plusSeconds(300),new ECKeyGenerator(Curve.P_256).keyID("test").generate())))
            mvc.perform(get("/api/uploads").header("Authorization","Bearer "+bad)).andExpect(status().isUnauthorized());
    }
    @Test void savesReadsAndDeletesOnlyTheAuthenticatedOwnersUpload() throws Exception {
        var result = mvc.perform(multipart("/api/uploads").file(csv(TransactionCsvReader.HEADER+"\np1,2026-01-01T12:00:00Z,10,USD,SUCCESS,100\n"))
                .header("Authorization","Bearer "+token(alice))).andExpect(status().isCreated()).andReturn();
        String id = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/uploads/"+id).header("Authorization","Bearer "+token(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[0].totalCount").value(1))
                .andExpect(jsonPath("$.findings[0].evaluations[0].state").value("INSUFFICIENT_DATA"));
        mvc.perform(get("/api/uploads/"+id).header("Authorization","Bearer "+token(bob))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/uploads/"+id).header("Authorization","Bearer "+token(bob))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/uploads/"+id).header("Authorization","Bearer "+token(alice))).andExpect(status().isNoContent());
        mvc.perform(get("/api/uploads/"+id).header("Authorization","Bearer "+token(alice))).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidCsvAndDoesNotExposeSimulatorInHostedProfile() throws Exception {
        mvc.perform(multipart("/api/uploads").file(csv("wrong header")).header("Authorization","Bearer "+token(alice))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/uploads").header("Authorization","Bearer "+token(alice))).andExpect(content().json("[]"));
        mvc.perform(get("/api/simulator/transactions").header("Authorization","Bearer "+token(alice))).andExpect(status().isForbidden());
    }
    @Test void restrictsCorsToTheDashboard() throws Exception {
        mvc.perform(options("/api/uploads").header("Origin","https://example.com").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://example.com"));
        mvc.perform(options("/api/uploads").header("Origin","https://evil.example").header("Access-Control-Request-Method","POST"))
                .andExpect(status().isForbidden());
    }
    @Test void privateAiContextIsOwnerScopedMinimizedAndRateLimited() throws Exception {
        var result=mvc.perform(multipart("/api/uploads").file(csv(TransactionCsvReader.HEADER+"\nsecret-id,2026-01-01T12:00:00Z,19.99,USD,SUCCESS,100\n"))
            .header("Authorization","Bearer "+token(alice))).andExpect(status().isCreated()).andReturn();
        String id=new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
        String path="/api/uploads/"+id+"/ai-context";
        mvc.perform(post(path).contentType("application/json").content("{\"currency\":\"USD\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization","Bearer "+token(bob)).contentType("application/json").content("{\"currency\":\"USD\"}"))
            .andExpect(status().isNotFound());
        for(int i=0;i<3;i++){
            var response=mvc.perform(post(path).header("Authorization","Bearer "+token(alice)).contentType("application/json").content("{\"currency\":\"USD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.observations[0].totalCount").value(1))
                .andExpect(jsonPath("$.observations[0].totalAmount").doesNotExist()).andExpect(jsonPath("$.upload").doesNotExist()).andReturn();
            org.junit.jupiter.api.Assertions.assertFalse(response.getResponse().getContentAsString().contains("secret-id"));
        }
        mvc.perform(post(path).header("Authorization","Bearer "+token(alice)).contentType("application/json").content("{\"currency\":\"USD\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
    }
    @Test void rateLimitsUploadAttemptsIndependentlyPerAccount() throws Exception {
        for(int i=0;i<5;i++)mvc.perform(multipart("/api/uploads").file(csv("invalid"))
            .header("Authorization","Bearer "+token(alice))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/uploads").file(csv("invalid")).header("Authorization","Bearer "+token(alice))).andExpect(status().isTooManyRequests());
        mvc.perform(multipart("/api/uploads").file(csv("invalid")).header("Authorization","Bearer "+token(bob))).andExpect(status().isBadRequest());
    }
}
