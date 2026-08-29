package com.mallota.controller;

import com.mallota.dto.request.BookingCreateRequest;
import com.mallota.dto.response.BookingResponse;
import com.mallota.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping
    public List<BookingResponse> list(@RequestParam @NotBlank String ownerId) {
        return bookingService.findByOwner(ownerId);
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID bookingId, @RequestParam @NotBlank String ownerId) {
        bookingService.cancel(bookingId, ownerId);
        return ResponseEntity.noContent().build();
    }
}
