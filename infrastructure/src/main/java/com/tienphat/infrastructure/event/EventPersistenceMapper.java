package com.tienphat.infrastructure.event;

import com.tienphat.domain.model.Event;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

@Mapper(componentModel = "spring")
public interface EventPersistenceMapper {

    EventJpaEntity toEntity(Event event);

    Event toDomain(EventJpaEntity entity);

    @ObjectFactory
    default Event createDomain(EventJpaEntity entity) {
        return Event.reconstitute(
                entity.getId(),
                entity.getOrganizerId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVenueName(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getSaleStartTime(),
                entity.getSaleEndTime(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
