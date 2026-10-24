package com.example.itline.service;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}