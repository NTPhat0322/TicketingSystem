package com.tienphat.infrastructure.user;

import com.tienphat.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;

@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {

    UserJpaEntity toEntity(User user);

    User toDomain(UserJpaEntity entity);

    @ObjectFactory
    default User createDomain(UserJpaEntity entity) {
        return User.reconstitute(
                entity.getId(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
