package com.malrota.recommendation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class MockSeatGenerator {

    private final Random random = new Random();

    public List<Seat> generate(String grade) {
        String g = grade == null ? "" : grade;

        if (g.contains("우등")) {
            // 우등: 4열 격자, 평소 3칸, 마지막 행 4칸(3열 채움), 8+1줄
            return build(8, 4, true);
        } else if (g.contains("프리미엄")) {
            // 프리미엄: 4열 격자, 항상 3칸(3열 안 채움), 7줄
            return build(7, 4, false);
        } else {
            // 일반: 5열 격자, 평소 4칸, 마지막 행 5칸(3열 채움), 10+1줄
            return build(10, 5, true);
        }
    }

    public List<Seat> generate() {
        return generate("우등");
    }

    /**
     * normalRows: 일반 줄 수
     * totalCols: 격자 열 수 (우등/프리미엄 4, 일반 5)
     * fillLastRow: 마지막 행에서 통로 열(3열)을 채우는지
     * 통로는 항상 3열
     */
    private List<Seat> build(int normalRows, int totalCols, boolean fillLastRow) {
        List<Seat> seats = new ArrayList<>();
        int totalRows = normalRows + 1;
        int aisleCol = 3; // 통로는 항상 3열

        for (int row = 1; row <= totalRows; row++) {
            boolean isLastRow = (row == totalRows);

            String position;
            if (row <= totalRows / 3.0) {
                position = "FRONT";
            } else if (row <= totalRows * 2 / 3.0) {
                position = "MIDDLE";
            } else {
                position = "BACK";
            }

            char letter = 'A';
            for (int displayCol = 1; displayCol <= totalCols; displayCol++) {
                // 통로 열(3열)은 평소엔 비움. 마지막 행 & fillLastRow면 채움
                boolean isAisle = (displayCol == aisleCol);
                if (isAisle && !(isLastRow && fillLastRow)) {
                    continue; // 이 자리는 좌석 없음 (통로)
                }

                String seatNo = row + String.valueOf(letter);
                String side = decideSide(displayCol, totalCols);
                boolean available = random.nextInt(10) < 7;
                seats.add(new Seat(seatNo, row, displayCol, position, side, available));
                letter++;
            }
        }
        return seats;
    }

    // 창가/통로 결정: 맨 왼쪽(1)과 맨 오른쪽(totalCols)이 창가
    private String decideSide(int displayCol, int totalCols) {
        return (displayCol == 1 || displayCol == totalCols) ? "WINDOW" : "AISLE";
    }
}