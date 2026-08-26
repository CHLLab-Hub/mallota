import time
import random
from nlu_extractor import WatsonxNluExtractor, ConversationParseRequest

def run_evaluation():
    extractor = WatsonxNluExtractor()
    
    # 10대 검증 테스트셋
    test_cases = [
        {
            "id": "TC-01 (표준 발화)",
            "text": "내일 오전 9시 서울에서 대전 가는 우등 버스 한 장 예약해줘",
            "expected": {"departure": "서울", "arrival": "대전", "busGrade": "EXCELLENT", "missing_len": 0}
        },
        {
            "id": "TC-02 (보행 불편/앞쪽통로)",
            "text": "다리가 불편해서 계단 오르기 힘든데 앞쪽 통로로 줘",
            "expected": {"needs": "WALKING_DIFFICULTY", "seat_front": "FRONT", "seat_aisle": "AISLE"}
        },
        {
            "id": "TC-03 (멀미/어르신 배려)",
            "text": "어르신 모시고 가는데 멀미가 심하셔서 중간 자리로 해줘",
            "expected": {"needs_sick": "MOTION_SICKNESS", "needs_elder": "ELDERLY_CARE", "seat_mid": "MIDDLE"}
        },
        {
            "id": "TC-04 (2인 예매)",
            "text": "할머니랑 둘이 탈 건데 창가 자리로 2장 끊어줘",
            "expected": {"passengers": 2, "needs": "ELDERLY_CARE", "seats": "WINDOW"}
        },
        {
            "id": "TC-05 (상대 시간: 2시간 뒤)",
            "text": "2시간 뒤에 출발하는 부산행 버스 있어?",
            "expected": {"arrival": "부산", "has_time": True}
        },
        {
            "id": "TC-06 (요일 + 14시)",
            "text": "이번 주 토요일 14시에 갈 거야",
            "expected": {"departureTime": "14:00", "has_date": True}
        },
        {
            "id": "TC-07 (돌아오는 10일 + 첫차)",
            "text": "돌아오는 10일 첫차로 찾아줘",
            "expected": {"servicePreference": "FIRST", "has_date": True}
        },
        {
            "id": "TC-08 (누락 필드 검증)",
            "text": "광주 가는 버스 찾아줘",
            "expected": {"arrival": "광주", "missing_has_dep": "departure"}
        },
        {
            "id": "TC-09 (상태 수정)",
            "text": "우등 말고 일반으로 아무거나 괜찮아",
            "currentState": {"departure": "서울", "arrival": "대전", "busGradePreference": "EXCELLENT"},
            "expected": {"busGradePreference": "GENERAL"}
        },
        {
            "id": "TC-10 (취소 인텐트)",
            "text": "아니 취소해줘",
            "expected": {"intent": "CANCEL"}
        },
        {
            "id": "TC-11 (~발/~행 복합)",
            "text": "서울발 대전행 14시 우등 버스 있어?",
            "expected": {"departure": "서울", "arrival": "대전", "departureTime": "14:00", "busGradePreference": "EXCELLENT"}
        },
        {
            "id": "TC-12 (동서울 + 첫차)",
            "text": "동서울에서 강릉 가는 제일 빠른 차로 줘",
            "expected": {"departure": "동서울", "arrival": "강릉", "servicePreference": "FIRST"}
        },
        {
            "id": "TC-13 (허리 통증/프리미엄)",
            "text": "허리가 너무 아파서 푹 젖혀지는 프리미엄으로 갈래",
            "expected": {"busGradePreference": "PREMIUM"}
        },
        {
            "id": "TC-14 (2인 동행)",
            "text": "손주 데리고 타는데 옆자리에 같이 앉아서 갈 거야",
            "expected": {"passengers": 2, "needs": "ELDERLY_CARE"}
        },
        {
            "id": "TC-15 (부정 표현: 창가 말고 통로)",
            "text": "햇빛 들어오면 눈부시니까 창가 말고 통로로 해줘",
            "expected": {"seats": "AISLE"}
        },
        {
            "id": "TC-16 (오늘 밤 막차)",
            "text": "오늘 밤 막차 시간표 좀 봐줘",
            "expected": {"servicePreference": "LAST", "has_date": True}
        },
        {
            "id": "TC-17 (사용자 정의)",
            "text": "돌아오는 10일에 부산으로 가는 거 가장 빠른걸로",
            "expected": {"has_date": True, "arrival": "부산", "servicePreference": "FIRST"}
        },
        {
            "id": "GS-01 (경상도: 부산행 + 시원찮아서)",
            "text": "내일 아침 일찍 마 부산 가는 거 우등으로 한 장 끊어도. 다리가 영 시원찮아서 앞자리로 해도.",
            "expected": {"arrival": "부산", "busGradePreference": "EXCELLENT", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "GS-02 (경상도: 손주 아 데꼬 + 2장)",
            "text": "손주 아 데꼬 대전 갈 낀데, 둘이 나란히 앉아가 갈라 카거든. 창가 쪽으로 표 두 장 해도.",
            "expected": {"arrival": "대전", "passengers": 2, "needs": "ELDERLY_CARE", "seats": "WINDOW"}
        },
        {
            "id": "GS-03 (경상도: 영감탱이 + 멀미)",
            "text": "영감탱이랑 둘이 갈라 카는데, 차만 타면 멀미가 심해가 중간 자리로 주이소.",
            "expected": {"passengers": 2, "needs_sick": "MOTION_SICKNESS", "seat_mid": "MIDDLE"}
        },
        {
            "id": "JL-01 (전라도: 점심 묵고 + 도가니)",
            "text": "아따 내일 점심 묵고 광주 내려갈랑게, 도가니가 쑤셔갖고 맨 앞자리 통로로 줘부러.",
            "expected": {"arrival": "광주", "timePreference": "AFTERNOON", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "JL-02 (전라도: 바깥양반 + 젤로 싼 놈)",
            "text": "우리 바깥양반이랑 나랑 둘이 갈 것인디, 젤로 싼 놈으로 두 장 끊어주쑈.",
            "expected": {"passengers": 2, "needs": "ELDERLY_CARE", "busGradePreference": "GENERAL"}
        },
        {
            "id": "CC-01 (충청도: 무릎 불편 + 앞쪽)",
            "text": "내일 아침에 서울 올라갈라 그러는디, 무릎이 영 불편해서 말이여, 앞쪽으로 끊어줘유.",
            "expected": {"arrival": "서울", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "CC-02 (충청도: 손주 녀석 + 이번 주말)",
            "text": "손주 녀석이랑 이번 주말에 부산 갈 거여. 둘이 같이 탈 거니께 2장 줘봐유.",
            "expected": {"arrival": "부산", "passengers": 2, "needs": "ELDERLY_CARE", "has_date": True}
        },
        {
            "id": "GW-01 (강원도: 제일 빠른 차 + 메스꺼우니까네)",
            "text": "동서울서 강릉 가는 거 제일 빠른 차로 하나 끊어주드래요. 속이 메스꺼우니까네 중간 창가로 주소.",
            "expected": {"departure": "동서울", "arrival": "강릉", "servicePreference": "FIRST", "needs_sick": "MOTION_SICKNESS"}
        },
        {
            "id": "JJ-01 (제주: 완도발 서울행 + 할망 + 하영)",
            "text": "할망 데리고 완도서 서울 올라가는디, 계단 오르기 하영 힘들다게. 맨 앞자리로 두 장 끊어줍서.",
            "expected": {"departure": "완도", "arrival": "서울", "passengers": 2, "needs": "ELDERLY_CARE", "seat_front": "FRONT"}
        },
        {
            "id": "JJ-02 (제주: 손지랑 고치 + 2장)",
            "text": "우리 손지 녀석이랑 고치 대전 갈 거우다. 둘이 나란히 앉아가게 표 두 장 줍써.",
            "expected": {"arrival": "대전", "passengers": 2, "needs": "ELDERLY_CARE"}
        },
        {
            "id": "JJ-03 (제주: 울렁울렁행 멀미 + 중간창가)",
            "text": "내일 아침 일찍이 부산 가는 거 젤 빠른 걸로 하나 줘보게. 속이 울렁울렁행 중간 창가로 줍서.",
            "expected": {"arrival": "부산", "servicePreference": "FIRST", "needs_sick": "MOTION_SICKNESS", "seats": "WINDOW"}
        },
        {
            "id": "JJ-04 (제주: 시방 광주행 + 젤로 싼 놈)",
            "text": "시방 바로 가는 광주 버스 이수과? 젤로 싼 놈으로 하나 끊어줍서.",
            "expected": {"arrival": "광주", "servicePreference": "FIRST", "busGradePreference": "GENERAL"}
        },
        {
            "id": "GS-04 (경상: 대구 + 낮에 + 억수로 편한 프리미엄)",
            "text": "아지매요, 내일 낮에 대구 가는 거 억수로 편한 프리미엄으로 하나 끊어도. 허리가 넘 쑤신다.",
            "expected": {"arrival": "대구", "timePreference": "AFTERNOON", "busGradePreference": "PREMIUM", "needs": "WALKING_DIFFICULTY"}
        },
        {
            "id": "GS-05 (경상: 영감재이 + 통로 2장)",
            "text": "우리 영감재이랑 둘이서 서울 가는데, 창가는 눈부시니께 통로 쪽으로 두 장 해도.",
            "expected": {"arrival": "서울", "passengers": 2, "needs": "ELDERLY_CARE", "seat_aisle": "AISLE"}
        },
        {
            "id": "GS-06 (경상: 시방 제일 빨리 + 대전행)",
            "text": "시방 제일 빨리 떠나는 대전행 버스 있능교? 암거나 빈자리 줘보소.",
            "expected": {"arrival": "대전", "servicePreference": "FIRST", "busGradePreference": "ANY"}
        },
        {
            "id": "GS-07 (경상: 이번 주 토욜날 저녁 + 부산 + 다리 절여가)",
            "text": "이번 주 토욜날 저녁에 부산 내려갈 긴데, 다리가 절여가 앞쪽으로 주이소.",
            "expected": {"arrival": "부산", "timePreference": "EVENING", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "JL-03 (전라: 징허게 다리 쑤셔 + 서울행)",
            "text": "워메 징허게 다리가 쑤셔분디, 내일 아침 서울 가는 거 맨 앞쪽 통로로 끊어줘부러.",
            "expected": {"arrival": "서울", "timePreference": "MORNING", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "JL-04 (전라: 딸래미랑 둘이 + 싸게싸게)",
            "text": "우리 딸래미랑 둘이서 광주 갈랑게, 싸게싸게 젤 빠른 차로 두 장 줘버려잉.",
            "expected": {"arrival": "광주", "passengers": 2, "servicePreference": "FIRST", "needs": "ELDERLY_CARE"}
        },
        {
            "id": "JL-05 (전라: 시방 저녁 + 옴팡지게 멀미)",
            "text": "시방 저녁 묵고 바로 출발하는 전주행 우등 버스 있냐? 멀미가 옴팡지게 나니까 중간 창가로 줘.",
            "expected": {"arrival": "전주", "timePreference": "EVENING", "busGradePreference": "EXCELLENT", "needs_sick": "MOTION_SICKNESS"}
        },
        {
            "id": "JL-06 (전라: 글피 아침 + 젤로 싼 놈)",
            "text": "글피 아침에 대전 갈랑게, 젤로 싼 놈으로 한 장만 끊어주쑈잉.",
            "expected": {"arrival": "대전", "timePreference": "MORNING", "busGradePreference": "GENERAL", "passengers": 1}
        },
        {
            "id": "JJ-05 (제주: 완도발 서울행 + 할망 + 하영)",
            "text": "할망 데리고 완도서 서울 올라가는디 계단 오르기 하영 힘들다게. 맨 앞자리로 두 장 끊어줍서.",
            "expected": {"departure": "완도", "arrival": "서울", "passengers": 2, "needs": "ELDERLY_CARE", "seat_front": "FRONT"}
        },
        {
            "id": "JJ-06 (제주: 손지 + 고치 + 창가 2장)",
            "text": "손지 녀석이랑 고치 대전 갈 거우다. 둘이 나란히 앉아가게 창가로 표 두 장 줍써.",
            "expected": {"arrival": "대전", "passengers": 2, "needs": "ELDERLY_CARE", "seats": "WINDOW"}
        },
        {
            "id": "JJ-07 (제주: 시방 광주행 + 울렁울렁)",
            "text": "시방 바로 가는 광주행 젤 빠른 버스 이수과? 속이 울렁울렁행 중간 자리로 줍서.",
            "expected": {"arrival": "광주", "servicePreference": "FIRST", "needs_sick": "MOTION_SICKNESS", "seats": "MIDDLE"}
        },
        {
            "id": "GW-02 (강원: 동서울발 강릉행 + 메스꺼우니까네)",
            "text": "동서울서 강릉 가는 거 제일 빠른 차로 하나 끊어주드래요. 속이 메스꺼우니까네 중간 창가로 주소.",
            "expected": {"departure": "동서울", "arrival": "강릉", "servicePreference": "FIRST", "needs_sick": "MOTION_SICKNESS"}
        },
        {
            "id": "GW-03 (강원: 원주발 부산행 + 할아바이 + 시큰)",
            "text": "원주서 부산 가는 우등 버스 할아바이하고 둘이 탈 건데 말이래요, 다리가 시큰해서 앞자리루 두 장 줘보소.",
            "expected": {"departure": "원주", "arrival": "부산", "passengers": 2, "busGradePreference": "EXCELLENT", "needs": "WALKING_DIFFICULTY"}
        },
        {
            "id": "GW-04 (강원: 속초 + 점심 묵고 + 눈부시잖소 통로)",
            "text": "내일 점심 묵고 속초 갈라카는디 창가는 눈부시잖소. 통로 쪽으로 하나 끊어주드래요.",
            "expected": {"arrival": "속초", "timePreference": "AFTERNOON", "seats": "AISLE"}
        },
        {
            "id": "CC-03 (충청: 서울행 + 무릎 불편 + 앞쪽 통로)",
            "text": "내일 아침에 서울 올라갈라 그러는디, 무릎이 영 불편해서 말이여, 앞쪽 통로로 끊어줘유.",
            "expected": {"arrival": "서울", "timePreference": "MORNING", "needs": "WALKING_DIFFICULTY", "seat_front": "FRONT"}
        },
        {
            "id": "CC-04 (충청: 손주 녀석 + 이번 주말 대전 2장)",
            "text": "손주 녀석이랑 이번 주말에 대전 갈 거여. 둘이 같이 탈 거니께 나란히 2장 줘봐유.",
            "expected": {"arrival": "대전", "passengers": 2, "needs": "ELDERLY_CARE"}
        },
        {
            "id": "CC-05 (충청: 천안발 광주행 + 14시 + 젤루 싼 놈)",
            "text": "천안서 광주 가는 14시 버스 젤루 싼 놈으로 하나 해줘봐유. 우등은 비싸잖여.",
            "expected": {"departure": "천안", "arrival": "광주", "departureTime": "14:00", "busGradePreference": "GENERAL"}
        },
        {
            "id": "SC-01 (좌석/인원 먼저 -> 출발/도착 나중)",
            "text": "앞자리 통로로 표 두 장 먼저 줘봐. 내일 대전 갈 건데 서울에서 아침 9시에 탈 거여.",
            "expected": {"departure": "서울", "arrival": "대전", "passengers": 2, "seat_front": "FRONT", "seat_aisle": "AISLE"}
        },
        {
            "id": "SC-02 (등급/무릎 먼저 -> 도착/시간 나중)",
            "text": "우등으로 끊어줘. 무릎이 시려 죽겠어. 광주 갈 거거든. 내일 점심 먹고.",
            "expected": {"arrival": "광주", "busGradePreference": "EXCELLENT", "needs": "WALKING_DIFFICULTY", "timePreference": "AFTERNOON"}
        },
        {
            "id": "SC-03 (인원/좌석 먼저 -> 시간/도착 나중)",
            "text": "둘이 탈 건데 창가 자리로 줘. 저녁 7시에 부산 가는 거.",
            "expected": {"arrival": "부산", "departureTime": "19:00", "passengers": 2, "seats": "WINDOW"}
        },
        {
            "id": "SC-04 (멀미/좌석/등급 먼저 -> 출발/도착 맨 끝)",
            "text": "멀미가 심해가 중간에 앉아야 되는데, 제일 싼 일반으로 한 장 줘보소. 동서울서 강릉 가는 거.",
            "expected": {"departure": "동서울", "arrival": "강릉", "busGradePreference": "GENERAL", "needs_sick": "MOTION_SICKNESS", "seat_mid": "MIDDLE"}
        },
        {
            "id": "SC-05 (단어 무작위 나열)",
            "text": "표 2장. 대구. 아침. 할머니랑.",
            "expected": {"arrival": "대구", "timePreference": "MORNING", "passengers": 2, "needs": "ELDERLY_CARE"}
        }
    ]
    random.shuffle(test_cases)

    print("================================================================")
    print("🚀 말로타(Malrota) watsonx.ai & NLU 인식률 종합 평가 시작")
    print("================================================================\n")

    passed_count = 0
    total_latency = 0.0

    for tc in test_cases:
        req = ConversationParseRequest(
            text=tc["text"],
            currentState=tc.get("currentState", {})
        )

        start_t = time.time()
        res = extractor.extract(req)
        elapsed = time.time() - start_t
        total_latency += elapsed

        # 검증 로직
        is_passed = True
        fail_reasons = []

        exp = tc["expected"]
        if "departure" in exp and res.departure != exp["departure"]:
            is_passed = False; fail_reasons.append(f"출발지 불일치(실제: {res.departure})")
        if "arrival" in exp and res.arrival != exp["arrival"]:
            is_passed = False; fail_reasons.append(f"도착지 불일치(실제: {res.arrival})")
        if "needs" in exp and exp["needs"] not in res.accessibilityNeeds:
            is_passed = False; fail_reasons.append(f"약자조건 누락({exp['needs']})")
        if "passengers" in exp and res.passengers != exp["passengers"]:
            is_passed = False; fail_reasons.append(f"인원수 불일치({res.passengers})")
        if "departureTime" in exp and res.departureTime != exp["departureTime"]:
            is_passed = False; fail_reasons.append(f"시각 불일치({res.departureTime})")
        if "servicePreference" in exp and res.servicePreference != exp["servicePreference"]:
            is_passed = False; fail_reasons.append(f"운행조건 불일치({res.servicePreference})")
        if "missing_has_dep" in exp and exp["missing_has_dep"] not in res.missingFields:
            is_passed = False; fail_reasons.append(f"누락필드 미감지({exp['missing_has_dep']})")

        status_str = "✅ PASS" if is_passed else "❌ FAIL"
        if is_passed: passed_count += 1

        print(f"[{status_str}] {tc['id']} (지연시간: {elapsed:.2f}s)")
        print(f"   발화: \"{tc['text']}\"")
        if not is_passed:
            print(f"   ⚠️ 실패 사유: {', '.join(fail_reasons)}")
        print(f"   결과: intent={res.intent}, dep={res.departure}, arr={res.arrival}, date={res.date}, time={res.departureTime or res.timePreference}, missing={res.missingFields}\n")

    accuracy = (passed_count / len(test_cases)) * 100
    avg_latency = total_latency / len(test_cases)

    print("================================================================")
    print(f"📊 최종 평가 결과 요약")
    print(f" - 현재 사용 모델: {extractor.model_id}")
    print(f" - 총 테스트 케이스: {len(test_cases)}건")
    print(f" - 성공 건수: {passed_count}건 / 실패: {len(test_cases) - passed_count}건")
    print(f" - 최종 슬롯 추출 정확도(Accuracy): {accuracy:.1f}%")
    print(f" - 평균 응답 속도(Avg Latency): {avg_latency:.2f}초")
    print("================================================================")

    # return accuracy, avg_latency

if __name__ == "__main__":
    run_evaluation()

# acc_list = []
# avg_lat = []
# num = 10
# for i in range(num):
    # if __name__ == "__main__":
    #     acc, lat = run_evaluation()
#         acc_list.append(acc)
#         avg_lat.append(lat)

# print("평균 정답률:",sum(acc_list)/num)
# print("평균 응답속도:", sum(avg_lat)/num)


