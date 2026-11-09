package br.com.fiap.oficina.dataprovider.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagem_processada")
@Getter
@NoArgsConstructor @AllArgsConstructor
public class MensagemProcessada {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "processada_em", nullable = false)
    private Instant processadaEm;
}
