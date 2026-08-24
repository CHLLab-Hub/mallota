package com.malrota.controller;

import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusSchedule;
import com.malrota.service.BusSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/buses")
public class BusSearchController {

    private final BusSearchService busSearchService;

    public BusSearchController(BusSearchService busSearchService) {
        this.busSearchService = busSearchService;
    }

    @PostMapping("/search")
    public List<BusSchedule> search(@Valid @RequestBody BusSearchRequest request) {
        return busSearchService.search(request);
    }
}