package br.com.fiap.oficina.dataprovider.observabilidade;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MicrometerMetricasGateway implements MetricasGateway {

    private final MeterRegistry registry;

    @Override
    public void ordemServicoCriada() {
        registry.counter("oficina.os.criadas").increment();
        EventosNewRelic.registrar("OficinaOsCriada", Map.of());
    }

    @Override
    public void transicaoDeStatus(StatusOS de, StatusOS para, Duration tempoNoStatusAnterior) {
        registry.counter("oficina.os.transicoes", "de", de.name(), "para", para.name()).increment();
        EventosNewRelic.registrar("OficinaOsTransicao", Map.of("de", de.name(), "para", para.name(),
                "segundosNoStatusAnterior", tempoNoStatusAnterior == null ? 0 : tempoNoStatusAnterior.toSeconds()));
        if (tempoNoStatusAnterior != null) {
            registry.timer("oficina.os.tempo.status", "status", de.name()).record(tempoNoStatusAnterior);
        }
    }

    @Override
    public void falhaDeIntegracao(String integracao, String motivo) {
        registry.counter("oficina.integracao.falhas", "integracao", integracao, "motivo", motivo).increment();
        EventosNewRelic.registrar("OficinaIntegracaoFalha", Map.of("servico", "os", "integracao", integracao, "motivo", motivo));
    }

    @Override
    public void sagaTransicao(String de, String para) {
        registry.counter("oficina.saga.transicoes", "de", de, "para", para).increment();
        EventosNewRelic.registrar("OficinaSagaTransicao", Map.of("de", de, "para", para));
    }

    @Override
    public void eventoForaDeEstado(String tipo, String estado) {
        registry.counter("oficina.saga.eventos.fora.de.estado", "tipo", tipo, "estado", estado).increment();
        EventosNewRelic.registrar("OficinaSagaEventoForaDeEstado", Map.of("tipo", tipo, "estado", estado));
    }
}
