package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.ServicoIndisponivelException;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.entrypoint.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ProdutoRestGateway implements ProdutoGateway {

    private static final String EMISSOR = "oficina-os";
    private static final String AUDIENCIA = "oficina-execucao";

    private final RestClient rest;
    private final JwtEncoder jwtEncoder;

    public ProdutoRestGateway(@Value("${app.execucao.base-url}") String baseUrl, JwtEncoder jwtEncoder) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(1));
        fabrica.setReadTimeout(Duration.ofSeconds(2));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica).build();
        this.jwtEncoder = jwtEncoder;
    }

    private record ProdutoInterno(Long id, String nome, TipoProduto tipo, BigDecimal precoUnitario) {}

    @Override
    public Optional<Produto> buscarPorId(Long id) {
        try {
            ProdutoInterno p = rest.get().uri("/internal/produtos/{id}", id)
                    .headers(h -> {
                        h.setBearerAuth(tokenDeServico());
                        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                        if (correlationId != null) {
                            h.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    })
                    .retrieve()
                    .body(ProdutoInterno.class);
            return Optional.of(new Produto(p.id(), p.nome(), p.tipo(), p.precoUnitario()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Serviço de Execução indisponível ao consultar o produto " + id, e);
        }
    }

    private String tokenDeServico() {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .audience(List.of(AUDIENCIA))
                .subject(EMISSOR)
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(60))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
