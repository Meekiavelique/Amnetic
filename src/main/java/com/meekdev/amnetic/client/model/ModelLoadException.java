package com.meekdev.amnetic.client.model;

public class ModelLoadException extends RuntimeException {
    public ModelLoadException(String message) { super(message); }
    public ModelLoadException(String message, Throwable cause) { super(message, cause); }
}
