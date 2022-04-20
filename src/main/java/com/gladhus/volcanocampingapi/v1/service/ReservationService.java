package com.gladhus.volcanocampingapi.v1.service;

import com.gladhus.volcanocampingapi.domain.Reservation;
import com.gladhus.volcanocampingapi.domain.ReservationStatus;
import com.gladhus.volcanocampingapi.exception.GenericException;
import com.gladhus.volcanocampingapi.exception.InvalidDatesException;
import com.gladhus.volcanocampingapi.exception.ReservationNotFoundException;
import com.gladhus.volcanocampingapi.repository.ReservationRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public Reservation createReservation(Reservation reservation) throws GenericException {

        // Check if checkin date is before checkout date
        if (reservation.getCheckin().isAfter(reservation.getCheckout())) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The checkout date should be after the checkin date.");
        }

        // Check if reservation is < 3 days
        if (ChronoUnit.DAYS.between(reservation.getCheckin(), reservation.getCheckout()) > 3) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The checkout date cannot be more than 3 days after the checkin date.");
        }

        // Check if checkout is at least one day after checkin
        if (ChronoUnit.DAYS.between(reservation.getCheckin(), reservation.getCheckout()) < 1) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The checkout date should be at least a day after the checkin date.");
        }

        // Check if the checkin is at least one day in the future
        if (!reservation.getCheckin().isAfter(LocalDate.now())) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The checkin date needs to be at least one day in the future.");
        }

        // Check that the checkout date is no more than one month in the future
        if (!reservation.getCheckout().isBefore(LocalDate.now().plusMonths(1))) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The checkout date cannot be more than a month in the future.");
        }

        // Get list of reservations within date range
        List<Reservation> reservationsWithinDateRange = reservationRepository.findByCheckoutIsBetweenOrCheckinIsBetweenAndStatus(reservation.getCheckin(), reservation.getCheckout(), reservation.getCheckin(), reservation.getCheckout(), ReservationStatus.ACTIVE).orElse(new ArrayList<>());

        // Check that the dates selected are available
        if (!getAvailableDates(reservation.getCheckin(), reservation.getCheckout(), reservationsWithinDateRange)
                .containsAll(reservation.getCheckin().datesUntil(reservation.getCheckout()).toList())) {
            throw new InvalidDatesException(HttpStatus.BAD_REQUEST, "The dates selected are not available.");
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservationRepository.save(reservation);

    }



    public Reservation getReservation(String id) throws GenericException{
        return reservationRepository.findById(id).orElseThrow(ReservationNotFoundException::new);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Reservation cancelReservation(String id) throws GenericException {
        Reservation reservation = reservationRepository.findByIdAndStatus(id, ReservationStatus.ACTIVE).orElseThrow(ReservationNotFoundException::new);

        reservation.setStatus(ReservationStatus.CANCELLED);

        return reservation;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public Set<LocalDate> getAvailabilities(LocalDate startDate, LocalDate endDate) throws GenericException {

        // TODO : Check if endDate is max a month in the future

        List<Reservation> reservations = reservationRepository.findByCheckinIsBetweenOrCheckoutIsBetweenAndStatus(startDate, endDate, startDate, endDate, ReservationStatus.ACTIVE).orElse(new ArrayList<>());
        return getAvailableDates(startDate, endDate, reservations);
    }

    private Set<LocalDate> getAvailableDates(LocalDate startDate, LocalDate endDate, List<Reservation> reservations) {
        Set<LocalDate> alreadyReservedDates = reservations.stream().flatMap(reservation -> reservation.getCheckin().datesUntil(reservation.getCheckout())).collect(Collectors.toSet());

        return startDate.datesUntil(endDate.plusDays(1)).filter(date -> !alreadyReservedDates.contains(date)).collect(Collectors.toCollection(TreeSet::new));
    }
}