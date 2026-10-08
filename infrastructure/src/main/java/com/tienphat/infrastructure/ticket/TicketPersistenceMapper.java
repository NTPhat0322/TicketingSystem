package com.tienphat.infrastructure.ticket;

import com.tienphat.domain.model.Ticket;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

/** Maps the pure Ticket aggregate to its infrastructure row and back. */
@Mapper(componentModel = "spring")
public interface TicketPersistenceMapper {

    TicketJpaEntity toEntity(Ticket ticket);

    Ticket toDomain(TicketJpaEntity entity);

    @ObjectFactory
    default Ticket createDomain(TicketJpaEntity entity) {
        return Ticket.reconstitute(
                entity.getId(),
                entity.getTicketCode(),
                entity.getOrderItemId(),
                entity.getTicketTypeId(),
                entity.getOwnerUserId(),
                entity.getStatus(),
                entity.getIssuedAt(),
                entity.getCheckedInAt());
    }
}
