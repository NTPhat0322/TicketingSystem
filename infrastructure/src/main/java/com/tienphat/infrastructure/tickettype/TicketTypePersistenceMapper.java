package com.tienphat.infrastructure.tickettype;

import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.vo.Money;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TicketTypePersistenceMapper {

    TicketTypeJpaEntity toEntity(TicketType ticketType);

    TicketType toDomain(TicketTypeJpaEntity entity);

    default BigDecimal map(Money price) {
        return price == null ? null : price.getAmount();
    }

    default Money map(BigDecimal price) {
        return price == null ? null : Money.of(price);
    }

    @ObjectFactory
    default TicketType createDomain(TicketTypeJpaEntity entity) {
        return TicketType.reconstitute(
                entity.getId(),
                entity.getEventId(),
                entity.getName(),
                map(entity.getPrice()),
                entity.getTotalQuantity(),
                entity.getSoldQuantity(),
                entity.getMaxPerUser(),
                entity.getHoldDurationSec(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
