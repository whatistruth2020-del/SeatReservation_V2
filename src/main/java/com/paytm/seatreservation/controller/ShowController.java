package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.CreateShowResponse;
import com.paytm.seatreservation.dto.ShowStateResponse;
import com.paytm.seatreservation.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    public ResponseEntity<CreateShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        CreateShowResponse response = showService.createShow(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowStateResponse> getShowState(@PathVariable Long id) {
        ShowStateResponse response = showService.getShowState(id);
        return ResponseEntity.ok(response);
    }
}