package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.dataprovider.persistence.entity.SagaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository extends JpaRepository<SagaEntity, UUID> {

    Optional<SagaEntity> findByOsId(Long osId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaEntity s WHERE s.id = :id")
    Optional<SagaEntity> travarPorId(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaEntity s WHERE s.osId = :osId")
    Optional<SagaEntity> travarPorOs(Long osId);

    @Query("SELECT s.id FROM SagaEntity s WHERE s.prazoEm <= :agora ORDER BY s.prazoEm")
    List<UUID> listarVencidas(Instant agora, Pageable pagina);
}
