package br.com.fiap.oficina.dataprovider.mensageria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MensagemProcessadaRepository extends JpaRepository<MensagemProcessada, UUID> {
}
