package com.mallota.dto.response;

import com.mallota.domain.BookingEntity;

import java.time.Instant;

public record BookingResponse(
        String id,
        BusSchedule bus,
        String seatNo,
        int passengers,
        int totalFare,
        Instant createdAt
) {
    public static BookingResponse from(BookingEntity booking) {
        return new BookingResponse(
                booking.getId().toString(),
                new BusSchedule(
                        booking.getRouteId(), booking.getGrade(), booking.getDeparture(), booking.getArrival(),
                        booking.getDepartureTime(), booking.getArrivalTime(), booking.getCharge()
                ),
                booking.getSeatNo(), booking.getPassengers(), booking.getTotalFare(), booking.getCreatedAt()
        );
    }
}
