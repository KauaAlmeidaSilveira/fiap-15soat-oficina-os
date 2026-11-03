package br.com.fiap.oficina.dataprovider.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OutboxMensagem {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String fila;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String corpo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;
}
