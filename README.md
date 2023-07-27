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
- START 버튼
  - 게임 누른 사람 식별하기 위해 무음 셀프카메라 찍히는 기능
  - ~~전체 게임수 + 1 (화면에 안보이게)~~
- ~~화면 상단에 계산하기 전까지 게임 수, 시간 합계 보이게 하기~~
- 기기 실행 시 자동으로 앱 실행시키기
- ~~앱 실행중에 전체 화면으로 보이게 하기~~
- ~~버튼 클릭 시 사운드 출력해주기~~

# 통신
- Server에서 Client로 보내는 데이터
  - START
    - 타이머를 시작시킨다.
  - END
    - 타이머를 종료시킨다.
  - POLL
    - 작동 중인지 STATUS 데이터를 보내준다.
  - CLOSE
    - 게임 전체 수를 초기화 시킨다.
  - RESET
    - 계산된 게임에서의 판 수를 초기화 시킨다.
- Client에서 Server로 보내는 데이터
  - START
    - 게임을 시작하겠다는 신호를 보낸다.
  - END
    - 게임을 끝내겠다는 신호를 보낸다.
  - STATUS
    - 1분에 1회 메세지를 보낸다.
    - POLL 신호를 받았을 때 응답해준다.

# 데이터 형식
- 테이블 번호 / 현재 시각 / Function / (게임 전체 수) / (Data) / CR(Carriage Return)
- 테이블 번호
  - 000 ~ 999
- 현재시각
  - YYMMDD HHMMSS
- Function
  - START
  - END
  - STATUS
  - POLL
  - CLOSE
  - RESET
- 게임 전체 수
  - 000 ~ 999
- Data
  - HHMM
- CR
  - x0d
