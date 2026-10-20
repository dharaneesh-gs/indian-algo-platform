package in.trading.platform;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("time",Instant.now().toString(),"error","BAD_REQUEST","message",ex.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("time",Instant.now().toString(),"error","BLOCKED","message",ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("time",Instant.now().toString(),"error","VALIDATION","message","Request validation failed."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> server(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("time",Instant.now().toString(),"error","SERVER_ERROR","message","Request failed. Check server logs."));
    }
}
