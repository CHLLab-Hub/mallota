package com.malrota.controller;

import com.malrota.dto.request.SeatRecommendRequest;
import com.malrota.recommendation.SeatRecommendation;
import com.malrota.service.SeatRecommendService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatRecommendService seatRecommendService;

    public SeatController(SeatRecommendService seatRecommendService) {
        this.seatRecommendService = seatRecommendService;
    }

    @PostMapping("/recommend")
    public SeatRecommendation recommend(@RequestBody SeatRecommendRequest request) {
        return seatRecommendService.recommend(request);
    }
}