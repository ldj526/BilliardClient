# Client
- 타이머 화면
  - ~~타이머 추가~~
    - ~~시작 버튼 클릭 시 종료 버튼으로 변경~~
  - ~~일반 사용자는 보이지 않는 곳에 설정화면으로 이동하는 기능~~
    - ~~Long Click 시 설정창으로 이동~~
- 설정 화면
  - ~~테이블 번호를 적는 EditText 생성~~
  - ~~IP 주소를 적는 EditText 생성~~
  - ~~저장 버튼 클릭 시 IP 주소와 테이블 번호 전달~~
  - ~~취소 버튼 시 타이머 화면으로 되돌아가기~~
- 기기 실행 시 자동으로 앱 실행시키기
- 앱 실행중에 전체 화면으로 보이게 하기

# 통신
- 서버에서 강제로 타이머 실행, 종료시키기
  - Client에서 Server로부터 정보를 받고 Server로 다시 신호 보내기
  - Client로부터 정보 받고 받았다는 정보 다시 Client로 보내기
- 간헐적으로 신호를 보내 Client가 정상 작동하는지 확인
  - 신호를 받은 Client에서 받았다는 정보를 다시 Server로 전송
- 1분에 1회 Client에서 Server로 신호 보내기

# 데이터 형식
- Function / 현재 시각 / Data / CR(Carriage Return)
- 타이머 시작을 했을 때 데이터 보내기
  - 현재시각 - YYMMDD HHMMSS
  - Data - HHMM
  - CR은 X0d 로 표시
- Function
  - START(시작 시)
  - END(종료 시)
  - STATUS(Client에서 1분에 1회 보낼 시)
  - POLL(Server에서 Client 작동 확인 여부)
  - ANSWER(신호 받았을 시 답)