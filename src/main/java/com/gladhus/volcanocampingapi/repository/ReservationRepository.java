package com.gladhus.volcanocampingapi.repository;

import com.gladhus.volcanocampingapi.domain.Reservation;
import com.gladhus.volcanocampingapi.domain.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.MANDATORY)
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Reservation> findByIdAndStatus(String id, ReservationStatus status);

    /**
     * This repository query uses a PESSIMISTIC_WRITE lock on the table to ensure repeatable reads
     * and avoid conflicts with concurrent requests.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("from Reservation r where r.status in :status and (r.checkin between :fromDate and :toDate or r.checkout between :fromDate and :toDate)")
    Optional<List<Reservation>> findByCheckoutOrCheckinIsBetweenAndStatusForUpdate(LocalDate fromDate, LocalDate toDate, ReservationStatus status);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query("from Reservation r where r.status in :status and (r.checkin between :fromDate and :toDate or r.checkout between :fromDate and :toDate)")
    Optional<List<Reservation>> findByCheckoutOrCheckinIsBetweenAndStatus(LocalDate fromDate, LocalDate toDate, ReservationStatus status);
}