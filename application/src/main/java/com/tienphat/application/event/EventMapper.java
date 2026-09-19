package com.tienphat.application.event;

import com.tienphat.domain.model.Event;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventMapper {

    EventResult toResult(Event event);
}
