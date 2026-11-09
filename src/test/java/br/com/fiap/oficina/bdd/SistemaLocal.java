package br.com.fiap.oficina.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Cliente HTTP dos serviços do docker-compose, com token de sessão igual ao emitido pela Lambda. */
final class SistemaLocal {

    static final String OS = "http://localhost:8081";
    static final String EXECUCAO = "http://localhost:8082";
    static final String BILLING = "http://localhost:8083";
    static final String MAILPIT = "http://localhost:8025";
    static final String MERCADO_PAGO = "http://localhost:8089";
    // Acima do VisibilityTimeout de 60 s: mensagem entregue a um listener que morreu só volta depois dele.
    private static final long ESPERA_MS = 120_000;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String token;

    SistemaLocal() {
        try {
            String pem = Files.readString(Path.of(".local/chaves/sessao.private.key"))
                    .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            RSAPrivateKey chave = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                    .issuer("http://oficina-local")
                    .audience("oficina-api")
                    .subject("bdd@oficina.local")
                    .claim("roles", List.of("ADMIN", "RECEPCAO", "OPERADOR"))
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .jwtID(UUID.randomUUID().toString())
                    .build());
            jwt.sign(new RSASSASigner(chave));
            token = jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Rode scripts/gerar-chaves-locais.sh antes do BDD", e);
        }
    }

    record Resposta(int status, String corpo) {}

    Resposta enviar(String metodo, String url, Object corpo, boolean autenticado) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                    .header("Content-Type", "application/json");
            if (autenticado) {
                req.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> resposta = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            return new Resposta(resposta.statusCode(), resposta.body());
        } catch (Exception e) {
            throw new IllegalStateException(metodo + " " + url, e);
        }
    }

    JsonNode exigir(String metodo, String url, Object corpo, int... aceitos) {
        Resposta resposta = enviar(metodo, url, corpo, true);
        for (int aceito : aceitos) {
            if (resposta.status() == aceito) {
                return ler(resposta.corpo());
            }
        }
        throw new AssertionError(metodo + " " + url + " respondeu " + resposta.status() + ": " + resposta.corpo());
    }

    JsonNode ler(String corpo) {
        try {
            return corpo == null || corpo.isBlank() ? json.createObjectNode() : json.readTree(corpo);
        } catch (Exception e) {
            throw new IllegalStateException(corpo, e);
        }
    }

    /** Fluxo assíncrono: espera a condição, com a última observação na mensagem de erro. */
    <T> T esperar(String descricao, Supplier<T> observar, java.util.function.Predicate<T> pronto) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        T ultimo = observar.get();
        while (!pronto.test(ultimo) && System.currentTimeMillis() < limite) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            ultimo = observar.get();
        }
        if (!pronto.test(ultimo)) {
            throw new AssertionError(descricao + ". Último valor: " + ultimo);
        }
        return ultimo;
    }

    String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
