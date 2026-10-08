package com.tienphat.infrastructure.outbox;

import com.tienphat.domain.model.OutboxEvent;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

/** Maps the pure OutboxEvent model to its PostgreSQL row and back. */
@Mapper(componentModel = "spring")
public interface OutboxEventPersistenceMapper {

    OutboxEventJpaEntity toEntity(OutboxEvent event);

    OutboxEvent toDomain(OutboxEventJpaEntity entity);

    @ObjectFactory
    default OutboxEvent createDomain(OutboxEventJpaEntity entity) {
        return OutboxEvent.reconstitute(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getPublishedAt());
    }
}
