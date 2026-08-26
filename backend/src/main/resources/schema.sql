-- 원본 bookings 테이블은 애플리케이션 저장용으로 유지한다.
-- pgAdmin용 조회 뷰는 열 순서와 최신 예매 우선 정렬을 제공한다.
-- Windows 문자셋에 영향을 받지 않도록 DB 식별자는 짧고 명확한 영문으로 둔다.
DROP VIEW IF EXISTS booking_history_view;

CREATE VIEW booking_history_view AS
SELECT
    id AS booking_id,
    departure || ' → ' || arrival AS route,
    departure_time AS departure_at,
    arrival_time AS arrival_at,
    grade AS bus_grade,
    seat_no AS seats,
    passengers AS passenger_count,
    charge AS fare_per_person,
    total_fare AS total_fare,
    created_at AS booked_at
FROM bookings
ORDER BY created_at DESC;
