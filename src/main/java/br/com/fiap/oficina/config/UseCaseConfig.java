package br.com.fiap.oficina.config;

import br.com.fiap.oficina.core.gateway.AutenticacaoInternaGateway;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.CorrelacaoGateway;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.MensageriaGateway;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.gateway.SagaGateway;
import br.com.fiap.oficina.core.gateway.TransacaoGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import br.com.fiap.oficina.core.usecase.AuthUseCase;
import br.com.fiap.oficina.core.usecase.AutenticacaoInternaUseCase;
import br.com.fiap.oficina.core.usecase.ClienteUseCase;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.core.usecase.OrquestradorSagaUseCase;
import br.com.fiap.oficina.core.usecase.VeiculoUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class UseCaseConfig {

    @Bean
    public ClienteUseCase clienteUseCase(ClienteGateway clienteGateway) {
        return new ClienteUseCase(clienteGateway);
    }

    @Bean
    public VeiculoUseCase veiculoUseCase(VeiculoGateway veiculoGateway, ClienteGateway clienteGateway) {
        return new VeiculoUseCase(veiculoGateway, clienteGateway);
    }

    @Bean
    public OrquestradorSagaUseCase orquestradorSagaUseCase(
            SagaGateway sagaGateway, OrdemServicoGateway ordemServicoGateway, MensageriaGateway mensageria,
            TransacaoGateway transacao, CorrelacaoGateway correlacao, MetricasGateway metricasGateway,
            @Value("${app.saga.prazo-etapa}") Duration prazoEtapa,
            @Value("${app.saga.prazo-decisao}") Duration prazoDecisao,
            @Value("${app.saga.margem-qr}") Duration margemQr) {
        return new OrquestradorSagaUseCase(sagaGateway, ordemServicoGateway, mensageria, transacao, correlacao, metricasGateway,
                new OrquestradorSagaUseCase.Prazos(prazoEtapa, prazoDecisao, margemQr), Clock.systemUTC());
    }

    @Bean
    public OrdemServicoUseCase ordemServicoUseCase(
            OrdemServicoGateway ordemServicoGateway, ClienteGateway clienteGateway, VeiculoGateway veiculoGateway,
            ProdutoGateway produtoGateway, OrquestradorSagaUseCase orquestrador, TransacaoGateway transacao,
            MetricasGateway metricasGateway) {
        return new OrdemServicoUseCase(ordemServicoGateway, clienteGateway, veiculoGateway, produtoGateway,
                orquestrador, transacao, metricasGateway);
    }

    @Bean
    public AuthUseCase authUseCase(UsuarioGateway usuarioGateway, CriptografiaSenhaGateway criptografiaSenhaGateway) {
        return new AuthUseCase(usuarioGateway, criptografiaSenhaGateway);
    }

    @Bean
    public AutenticacaoInternaUseCase autenticacaoInternaUseCase(AutenticacaoInternaGateway gateway) {
        return new AutenticacaoInternaUseCase(gateway);
    }
}
