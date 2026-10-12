package com.tienphat.presentation.ticket;

import com.tienphat.application.ticket.TicketResult;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.ticket.dto.TicketResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketDtoMapper {

    TicketResponse toResponse(TicketResult result);

    List<TicketResponse> toResponses(List<TicketResult> results);

    default PageResponse<TicketResponse> toPageResponse(PageResult<TicketResult> pageResult) {
        return new PageResponse<>(
                toResponses(pageResult.content()), pageResult.page(), pageResult.size(),
                pageResult.totalElements(), pageResult.totalPages());
    }
}
