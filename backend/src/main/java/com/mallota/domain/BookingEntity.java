package com.mallota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** PostgreSQL에 영구 보관하는 예매 내역. */
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_owner_created", columnList = "owner_id,created_at")
})
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 인증 전 MVP에서는 브라우저별 고정 식별자이며, 로그인 도입 후 사용자 ID로 교체한다.
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "route_id", nullable = false, length = 100)
    private String routeId;

    @Column(nullable = false, length = 80)
    private String grade;

    @Column(nullable = false, length = 100)
    private String departure;

    @Column(nullable = false, length = 100)
    private String arrival;

    @Column(name = "departure_time", nullable = false, length = 20)
    private String departureTime;

    @Column(name = "arrival_time", nullable = false, length = 20)
    private String arrivalTime;

    @Column(nullable = false)
    private int charge;

    @Column(name = "seat_no", nullable = false, length = 100)
    private String seatNo;

    @Column(nullable = false)
    private int passengers;

    @Column(name = "total_fare", nullable = false)
    private int totalFare;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BookingEntity() {
    }

    public BookingEntity(String ownerId, String routeId, String grade, String departure, String arrival,
                         String departureTime, String arrivalTime, int charge, String seatNo,
                         int passengers, int totalFare) {
        this.ownerId = ownerId;
        this.routeId = routeId;
        this.grade = grade;
        this.departure = departure;
        this.arrival = arrival;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.charge = charge;
        this.seatNo = seatNo;
        this.passengers = passengers;
        this.totalFare = totalFare;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getRouteId() { return routeId; }
    public String getGrade() { return grade; }
    public String getDeparture() { return departure; }
    public String getArrival() { return arrival; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public int getCharge() { return charge; }
    public String getSeatNo() { return seatNo; }
    public int getPassengers() { return passengers; }
    public int getTotalFare() { return totalFare; }
    public Instant getCreatedAt() { return createdAt; }
}
