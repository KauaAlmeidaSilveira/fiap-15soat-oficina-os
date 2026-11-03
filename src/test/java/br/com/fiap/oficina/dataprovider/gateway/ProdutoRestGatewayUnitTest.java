package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.ServicoIndisponivelException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdutoRestGatewayUnitTest {

    private static WireMockServer execucao;
    private static ProdutoRestGateway gateway;
    private static JwtDecoder decoder;

    @BeforeAll
    static void iniciar() throws Exception {
        execucao = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        execucao.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair par = generator.generateKeyPair();
        RSAPublicKey publica = (RSAPublicKey) par.getPublic();
        JWK jwk = new RSAKey.Builder(publica).privateKey((RSAPrivateKey) par.getPrivate()).build();
        gateway = new ProdutoRestGateway(execucao.baseUrl(), new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))));
        decoder = NimbusJwtDecoder.withPublicKey(publica).build();
    }

    @AfterAll
    static void parar() {
        execucao.stop();
    }

    @Test
    @DisplayName("Consulta o produto com token de serviço válido por 60 segundos")
    void consulta() {
        execucao.stubFor(get("/internal/produtos/1").willReturn(okJson("""
                {"id":1,"nome":"Filtro","tipo":"PECA","precoUnitario":45.00}""")));

        var produto = gateway.buscarPorId(1L).orElseThrow();

        assertThat(produto.nome()).isEqualTo("Filtro");
        assertThat(produto.tipo()).isEqualTo(TipoProduto.PECA);
        String header = execucao.findAll(getRequestedFor(urlEqualTo("/internal/produtos/1"))).get(0).getHeader("Authorization");
        Jwt token = decoder.decode(header.substring("Bearer ".length()));
        assertThat(token.getAudience()).containsExactly("oficina-execucao");
        assertThat(token.getClaimAsString("iss")).isEqualTo("oficina-os");
        assertThat(token.getExpiresAt()).isEqualTo(token.getIssuedAt().plusSeconds(60));
    }

    @Test
    @DisplayName("404 vira vazio; outros erros viram serviço indisponível")
    void erros() {
        execucao.stubFor(get("/internal/produtos/2").willReturn(aResponse().withStatus(404)));
        execucao.stubFor(get("/internal/produtos/3").willReturn(aResponse().withStatus(500)));

        assertThat(gateway.buscarPorId(2L)).isEmpty();
        assertThatThrownBy(() -> gateway.buscarPorId(3L)).isInstanceOf(ServicoIndisponivelException.class);
    }
}
