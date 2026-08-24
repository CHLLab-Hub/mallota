package com.malrota.recommendation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class MockSeatGenerator {

    private final Random random = new Random();

    // 좌석 28개 생성 (7열 x 4좌석: A, B 창가 / C, D 통로 느낌)
    public List<Seat> generate() {
        List<Seat> seats = new ArrayList<>();

        int totalRows = 7;  // 7열
        String[] columns = {"A", "B", "C", "D"};

        for (int row = 1; row <= totalRows; row++) {
            // 위치 결정: 앞(1~2열), 중간(3~5열), 뒤(6~7열)
            String position;
            if (row <= 2) {
                position = "FRONT";
            } else if (row <= 5) {
                position = "MIDDLE";
            } else {
                position = "BACK";
            }

            for (String col : columns) {
                // 창가/통로: A, D는 창가 / B, C는 통로
                String side = (col.equals("A") || col.equals("D")) ? "WINDOW" : "AISLE";

                // 예약 가능 여부: 랜덤하게 약 70%는 빈 자리
                boolean available = random.nextInt(10) < 7;

                seats.add(new Seat(row + col, position, side, available));
            }
        }

        return seats;
    }
}