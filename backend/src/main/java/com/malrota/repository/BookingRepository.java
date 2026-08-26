package com.malrota.repository;

import com.malrota.domain.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
