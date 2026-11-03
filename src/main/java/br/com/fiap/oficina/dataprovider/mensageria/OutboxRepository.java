package br.com.fiap.oficina.dataprovider.mensageria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMensagem, UUID> {

    // SKIP LOCKED permite várias réplicas publicando sem repetir o mesmo lote.
    @Query(value = """
            SELECT * FROM outbox WHERE publicado_em IS NULL
            ORDER BY criado_em LIMIT 50 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMensagem> travarPendentes();
}
