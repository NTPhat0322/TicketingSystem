package com.tienphat.application.usecase;

/**
 * A single application operation: one input, one output. Implemented by every use-case class.
 */
public interface UseCase<I, O> {

    O execute(I input);
}
