package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ClientePersistenceMapper.class, VeiculoPersistenceMapper.class})
public interface OrdemServicoPersistenceMapper {

    OrdemServico toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico entity);

    OsItem toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.OsItem item);
}
