package com.meekdev.amnetic.client.content;

// a file from a game that is broken or claims more than ContentLimits allows
public final class RejectedContentException extends IllegalArgumentException {

    public RejectedContentException(String message) {
        super(message);
    }
}
