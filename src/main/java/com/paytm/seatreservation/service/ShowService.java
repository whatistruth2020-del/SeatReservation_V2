package com.paytm.seatreservation.service;

import com.paytm.seatreservation.config.ReservationMetrics;
import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.CreateShowResponse;
import com.paytm.seatreservation.dto.SeatInfo;
import com.paytm.seatreservation.dto.ShowStateResponse;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.exception.SeatReservationException;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationMetrics metrics;

    @Transactional
    public CreateShowResponse createShow(CreateShowRequest request) {
        // Check if show name already exists
        if (showRepository.findByName(request.getName()).isPresent()) {
            throw SeatReservationException.internalError("Show with name '" + request.getName() + "' already exists");
        }

        Show show = Show.builder()
                .name(request.getName())
                .pricePaise(request.getPricePaise())
                .totalSeats(request.getSeats().size())
                .saleOpensAt(OffsetDateTime.now())
                .build();

        // Create seats
        for (String seatNumber : request.getSeats()) {
            Seat seat = Seat.builder()
                    .show(show)
                    .seatNumber(seatNumber)
                    .status(SeatStatus.AVAILABLE)
                    .build();
            show.addSeat(seat);
        }

        show = showRepository.save(show);

        // Initialize metrics
        metrics.updateSeatsAvailable(show.getId(), show.getTotalSeats());

        log.info("Created show: id={}, name={}, seats={}", show.getId(), show.getName(), show.getTotalSeats());

        return toCreateShowResponse(show);
    }

    @Transactional(readOnly = true)
    public ShowStateResponse getShowState(Long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> SeatReservationException.showNotFound(showId));

        List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumber(showId);

        List<SeatInfo> seatInfos = seats.stream()
                .map(s -> SeatInfo.builder()
                        .seatNumber(s.getSeatNumber())
                        .status(s.getStatus().name().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        Map<String, Long> statusCounts = seats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStatus().name().toLowerCase(),
                        Collectors.counting()
                ));

        long available = statusCounts.getOrDefault("available", 0L);
        long held = statusCounts.getOrDefault("held", 0L);
        long confirmed = statusCounts.getOrDefault("confirmed", 0L);

        return ShowStateResponse.builder()
                .id(show.getId())
                .name(show.getName())
                .pricePaise(show.getPricePaise())
                .perUserLimit(show.getPerUserLimit())
                .totalSeats(show.getTotalSeats())
                .availableSeats((int) available)
                .heldSeats((int) held)
                .confirmedSeats((int) confirmed)
                .seats(seatInfos)
                .statusCounts(statusCounts)
                .build();
    }

    private CreateShowResponse toCreateShowResponse(Show show) {
        List<SeatInfo> seatInfos = show.getSeats().stream()
                .map(s -> SeatInfo.builder()
                        .seatNumber(s.getSeatNumber())
                        .status(s.getStatus().name().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        return CreateShowResponse.builder()
                .id(show.getId())
                .name(show.getName())
                .pricePaise(show.getPricePaise())
                .perUserLimit(show.getPerUserLimit())
                .totalSeats(show.getTotalSeats())
                .seats(seatInfos)
                .build();
    }
}