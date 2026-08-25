package com.malrota.recommendation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class MockSeatGenerator {

    private final Random random = new Random();

    /** 등급에 맞는 좌석 배치 생성 */
    public List<Seat> generate(String grade) {
        String g = grade == null ? "" : grade;

        if (g.contains("우등")) {
            // 우등: 1~8줄 3칸 + 9줄 4칸 = 28석
            return build(8, 3, 4);
        } else if (g.contains("프리미엄")) {
            // 프리미엄: 1~7줄 3칸 = 21석 (뒷줄 추가 없음)
            return build(7, 3, 3);
        } else {
            // 일반/고속: 1~10줄 4칸 + 11줄 5칸 = 45석
            return build(10, 4, 5);
        }
    }

    // 등급 없이 호출하면 기본(우등)
    public List<Seat> generate() {
        return generate("우등");
    }

    /**
     * normalRows: 일반 줄 수, colsPerRow: 한 줄 칸 수, lastRowCols: 마지막 줄 칸 수
     */
    private List<Seat> build(int normalRows, int colsPerRow, int lastRowCols) {
        List<Seat> seats = new ArrayList<>();
        int totalRows = normalRows + 1; // 마지막 줄 포함

        for (int row = 1; row <= totalRows; row++) {
            boolean isLastRow = (row == totalRows);
            int cols = isLastRow ? lastRowCols : colsPerRow;

            // 위치: 앞 1/3 FRONT, 중간 MIDDLE, 뒤 1/3 BACK
            String position;
            if (row <= totalRows / 3.0) {
                position = "FRONT";
            } else if (row <= totalRows * 2 / 3.0) {
                position = "MIDDLE";
            } else {
                position = "BACK";
            }

            for (int col = 1; col <= cols; col++) {
                String seatNo = row + colLetter(col);
                String side = decideSide(col, cols);
                boolean available = random.nextInt(10) < 7; // 약 70% 빈자리
                seats.add(new Seat(seatNo, row, col, position, side, available));
            }
        }
        return seats;
    }

    // 칸 번호 → 알파벳 (1=A, 2=B, ...)
    private String colLetter(int col) {
        return String.valueOf((char) ('A' + col - 1));
    }

    // 창가/통로 결정
    // 3칸 배치: [A창][B통] [C창]  → 1=창가, 2=통로, 3=창가
    // 4칸 배치: [A창][B통] [C통][D창] → 1=창가, 끝=창가, 나머지 통로
    private String decideSide(int col, int totalCols) {
        if (totalCols <= 3) {
            // 3칸: 1번(A)과 3번(C)이 창가
            return (col == 1 || col == 3) ? "WINDOW" : "AISLE";
        } else {
            // 4칸 이상: 맨 왼쪽(1)과 맨 오른쪽(totalCols)이 창가
            return (col == 1 || col == totalCols) ? "WINDOW" : "AISLE";
        }
    }
}