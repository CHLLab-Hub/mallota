package com.mallota.service;

import com.mallota.domain.BookingEntity;
import com.mallota.dto.request.BookingCreateRequest;
import com.mallota.dto.response.BookingResponse;
import com.mallota.exception.BusinessException;
import com.mallota.exception.ErrorCode;
import com.mallota.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public BookingResponse create(BookingCreateRequest request) {
        int expectedTotal;
        try {
            expectedTotal = Math.multiplyExact(request.charge(), request.passengers());
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "요금 계산 값이 올바르지 않습니다.");
        }
        if (request.totalFare() != expectedTotal) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "총 요금이 인원수와 1인 요금에 맞지 않습니다.");
        }

        BookingEntity booking = new BookingEntity(
                request.ownerId(), request.routeId(), request.grade(), request.departure(), request.arrival(),
                request.departureTime(), request.arrivalTime(), request.charge(), request.seatNo(),
                request.passengers(), request.totalFare()
        );
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByOwner(String ownerId) {
        return bookingRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    public void cancel(UUID bookingId, String ownerId) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "예매 내역을 찾을 수 없습니다."));
        bookingRepository.delete(booking);
    }
}
