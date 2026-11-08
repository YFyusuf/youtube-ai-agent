package com.curiosity.agent_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Beklenen iş kuralı hatalarını (bulunamadı, geçersiz istek) 400 + okunur
 * mesaj olarak döndürür; yoksa hepsi mesajsız 500 olurdu ve arayüzde
 * kullanıcıya ne olduğu gösterilemezdi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBusinessError(RuntimeException e) {
        return Map.of("message", e.getMessage() == null ? "Geçersiz istek." : e.getMessage());
    }
}
