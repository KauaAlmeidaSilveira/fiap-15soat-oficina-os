package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.dataprovider.persistence.entity.HistoricoStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatusEntity, Long> {

    List<HistoricoStatusEntity> findByOsIdOrderByIdAsc(Long osId);
}
