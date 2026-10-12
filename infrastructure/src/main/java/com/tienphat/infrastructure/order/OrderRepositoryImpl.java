package com.tienphat.infrastructure.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** PostgreSQL adapter for the Order aggregate and its cascaded OrderItems. */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository, OrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(order));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByOrderCode(String orderCode) {
        return jpaRepository.findByOrderCode(orderCode).map(mapper::toDomain);
    }

    @Override
    public PageResult<Order> findAllByUserId(UUID userId, PageRequest pageRequest) {
        Page<UUID> idPage = jpaRepository.findIdsByUserId(
                userId, org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
        List<UUID> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), pageRequest.page(), pageRequest.size(), idPage.getTotalElements());
        }

        Map<UUID, OrderJpaEntity> loaded = jpaRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(OrderJpaEntity::getId, Function.identity()));
        List<Order> orders = ids.stream()
                .map(loaded::get)
                .filter(Objects::nonNull)
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(orders, pageRequest.page(), pageRequest.size(), idPage.getTotalElements());
    }
}
