package com.tienphat.application.tickettype;

import com.tienphat.domain.model.TicketType;
import com.tienphat.domain.vo.Money;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TicketTypeMapper {

    TicketTypeResult toResult(TicketType ticketType);

    default BigDecimal map(Money price) {
        return price == null ? null : price.getAmount();
    }
}
