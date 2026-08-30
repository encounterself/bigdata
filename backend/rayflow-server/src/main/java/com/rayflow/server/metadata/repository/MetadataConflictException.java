package com.rayflow.server.metadata.repository;

public class MetadataConflictException extends RuntimeException {
    public MetadataConflictException(String message) {
        super(message);
    }
}
