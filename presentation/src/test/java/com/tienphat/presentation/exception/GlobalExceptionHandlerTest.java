package com.tienphat.presentation.exception;

import com.tienphat.domain.exception.DomainException;
import com.tienphat.domain.exception.EventNotFoundException;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidCredentialsException;
import com.tienphat.domain.exception.InvalidEventDataException;
import com.tienphat.domain.exception.InvalidEventScheduleException;
import com.tienphat.domain.exception.InvalidEventStateException;
import com.tienphat.domain.exception.InvalidTicketTypeDataException;
import com.tienphat.domain.exception.TicketTypeConcurrentUpdateException;
import com.tienphat.domain.exception.TicketTypeNotAvailableException;
import com.tienphat.domain.exception.TicketTypeNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final Map<DomainException, HttpStatus> MAPPED_EXCEPTIONS = Map.ofEntries(
            Map.entry(new EventNotFoundException("event not found"), HttpStatus.NOT_FOUND),
            Map.entry(new TicketTypeNotFoundException("ticket type not found"), HttpStatus.NOT_FOUND),
            Map.entry(new TicketTypeConcurrentUpdateException("stale version"), HttpStatus.CONFLICT),
            Map.entry(new TicketTypeNotAvailableException("already closed"), HttpStatus.CONFLICT),
            Map.entry(new InvalidEventDataException("blank name"), HttpStatus.BAD_REQUEST),
            Map.entry(new InvalidEventScheduleException("start after end"), HttpStatus.BAD_REQUEST),
            Map.entry(new InvalidEventStateException("already cancelled"), HttpStatus.BAD_REQUEST),
            Map.entry(new InvalidTicketTypeDataException("negative price"), HttpStatus.BAD_REQUEST),
            Map.entry(new InvalidCredentialsException("invalid credentials"), HttpStatus.UNAUTHORIZED),
            Map.entry(new ForbiddenOperationException("forbidden"), HttpStatus.FORBIDDEN));

    @Test
    @DisplayName("each of the 9 mapped domain exceptions produces its expected HttpStatus and the exception's message as detail")
    void mappedDomainExceptions_produceExpectedStatusAndDetail() {
        MAPPED_EXCEPTIONS.forEach((exception, expectedStatus) -> {
            ProblemDetail problem = handler.handleDomainException(exception);

            assertThat(problem.getStatus()).as(exception.getClass().getSimpleName()).isEqualTo(expectedStatus.value());
            assertThat(problem.getDetail()).as(exception.getClass().getSimpleName()).isEqualTo(exception.getMessage());
        });
    }

    @Test
    @DisplayName("an unmapped DomainException subclass defaults to a 500 ProblemDetail")
    void unmappedDomainException_defaultsTo500() {
        DomainException unmapped = new DomainException("boom") {};

        ProblemDetail problem = handler.handleDomainException(unmapped);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with two field errors produces a 400 ProblemDetail listing both fields")
    void methodArgumentNotValid_producesFieldErrorsList() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "venueName", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ProblemDetail problem = handler.handleMethodArgumentNotValid(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors)
                .extracting(error -> error.get("field"))
                .containsExactlyInAnyOrder("name", "venueName");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException (malformed UUID path variable) produces a 400 ProblemDetail")
    void methodArgumentTypeMismatch_producesBadRequest() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", null, new IllegalArgumentException("invalid UUID"));

        ProblemDetail problem = handler.handleMethodArgumentTypeMismatch(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("ConstraintViolationException (@Validated @RequestParam failure) produces a 400 ProblemDetail")
    void constraintViolation_producesBadRequest() {
        ConstraintViolationException ex = new ConstraintViolationException("page: must be greater than or equal to 0", Set.of());

        ProblemDetail problem = handler.handleConstraintViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("HttpMessageNotReadableException (malformed JSON body) produces a 400 ProblemDetail")
    void httpMessageNotReadable_producesBadRequest() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("malformed JSON", mock(HttpInputMessage.class));

        ProblemDetail problem = handler.handleMessageNotReadable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("NoResourceFoundException (no route matches the request) produces a 404 ProblemDetail")
    void noResourceFound_producesNotFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/", "/");

        ProblemDetail problem = handler.handleNoResourceFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("AccessDeniedException produces a 403 ProblemDetail with a fixed, non-leaking detail")
    void accessDenied_producesForbidden() {
        AccessDeniedException ex = new AccessDeniedException("user lacks ROLE_ADMIN for this operation");

        ProblemDetail problem = handler.handleAccessDenied(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail()).doesNotContain("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a generic Exception produces a 500 ProblemDetail with no leaked message or stack trace")
    void genericException_producesNonLeaking500() {
        Exception ex = new RuntimeException("password=hunter2 connection string leaked here");

        ProblemDetail problem = handler.handleUnexpected(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("hunter2");
    }
}
