package com.echo.exception;

/**
 * Thrown when a signed transaction is submitted by a user that differs
 * from the one originally bound to the subscription. Prevents replay of
 * a leaked transaction to transfer premium between accounts.
 */
public class SubscriptionOwnershipException extends RuntimeException {
    public SubscriptionOwnershipException(String message) {
        super(message);
    }
}
