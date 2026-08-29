package com.mallota.recommendation;

public record Seat(
        String seatNo,      // 좌석 번호 (예: "3A")
        int row,            // 줄 번호 (1부터)
        int column,         // 칸 번호 (1=맨왼쪽, 왼→오른쪽 순)
        String position,    // 위치: FRONT, MIDDLE, BACK
        String side,        // 창가/통로: WINDOW, AISLE
        boolean available   // 예약 가능 여부
) {
}