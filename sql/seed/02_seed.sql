/* CJAirPort seed — 최소 운영 데이터. 01_tables.sql 실행 후 수행.
   비밀번호/성명은 ARIA-256(개발키 01234567890123456789012345678901)로 암호화된 값.
   ⚠️ 운영 배포 시 실제 키로 재생성한 값으로 교체할 것. (docs/security.md)
   한글 리터럴은 nvarchar 안전을 위해 N'' 접두 사용. */

/* 공통코드: 감사유형(AT) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('AT', N'감사유형', 'MENU',   N'메뉴접속', 'Y'),
  ('AT', N'감사유형', 'READ',   N'조회',     'Y'),
  ('AT', N'감사유형', 'CREATE', N'등록',     'Y'),
  ('AT', N'감사유형', 'UPDATE', N'수정',     'Y'),
  ('AT', N'감사유형', 'DELETE', N'삭제',     'Y'),
  ('AT', N'감사유형', 'DOWNLOAD', N'다운로드', 'Y'),
  ('AT', N'감사유형', 'LOGIN',  N'로그인',  'Y'),
  ('AT', N'감사유형', 'LOGOUT', N'로그아웃', 'Y');

/* 공통코드: 근무지역(LO) 예시 — 시스템 코드(user_input=N, 기본값) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('LO', N'근무지역', 'T1', N'여객터미널', 'Y'),
  ('LO', N'근무지역', 'T2', N'화물터미널', 'Y');

/* 사용자 추가 허용 구분(방문사유 VR) — user_input='Y' 로 개설. 화면 등록 시 select 에 노출됨. */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
  ('VR', N'방문사유', 'MEETING', N'회의', 'Y', 'Y'),
  ('VR', N'방문사유', 'WORK',    N'공사', 'Y', 'Y');

/* 출입구역(AR) — 출입권한관리 트리의 최상위(tb_ac_group 동기화 기준). code_id → ar_code */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
  ('AR', N'출입구역', 'GATE', N'게이트구역', 'Y', 'Y'),
  ('AR', N'출입구역', 'RAMP', N'램프구역',   'Y', 'Y');

/* 공통코드: 차종(CT) — 차량등록관리에서 사용. 시스템 코드(user_input=N 기본) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('CT', N'차종', '01', N'승용차', 'Y'),
  ('CT', N'차종', '02', N'SUV',    'Y'),
  ('CT', N'차종', '03', N'화물차', 'Y'),
  ('CT', N'차종', '04', N'트럭',   'Y'),
  ('CT', N'차종', '05', N'기타',   'Y');

/* 공통코드: 기관구분(CO) — 기관등록관리에서 사용. 시스템 코드(user_input=N 기본) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('CO', N'기관구분', '11', N'기관',   'Y'),
  ('CO', N'기관구분', '22', N'국영',   'Y'),
  ('CO', N'기관구분', '33', N'공사',   'Y'),
  ('CO', N'기관구분', '44', N'항공사', 'Y'),
  ('CO', N'기관구분', '55', N'업체',   'Y');

/* 인원구분(PT) — code_tag = 대응하는 발급구분(PTD) 코드 */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
  ('PT', N'인원구분', 'PT01', N'정규', 'PTD01', 'Y'),
  ('PT', N'인원구분', 'PT02', N'임시', 'PTD02', 'Y'),
  ('PT', N'인원구분', 'PT03', N'장기', 'PTD03', 'Y'),
  ('PT', N'인원구분', 'PT04', N'상주', 'PTD03', 'Y');

/* 발급구분(PTD) — code_tag = BiostarX 부모 사용자그룹 ID.
   ⚠️ BiostarX 환경마다 다르므로 대상 서버의 실제 그룹 ID 로 교체할 것. (integration.md) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
  ('PTD', N'발급구분', 'PTD01', N'정규등록', '14227', 'Y'),
  ('PTD', N'발급구분', 'PTD02', N'임시등록', '14236', 'Y'),
  ('PTD', N'발급구분', 'PTD03', N'장기등록', '14231', 'Y');

/* 인원상태(PS) — code_tag = BiostarX 사용자의 disabled 값(신규만 활성, 나머지는 비활성) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
  ('PS', N'인원상태', '01', N'신규', 'false', 'Y'),
  ('PS', N'인원상태', '02', N'정지', 'true',  'Y'),
  ('PS', N'인원상태', '03', N'퇴사', 'true',  'Y'),
  ('PS', N'인원상태', '04', N'회수', 'true',  'Y');

/* 카드상태(CS) — 카드 상태의 진실의 원천(tb_card.card_status 단일 컬럼).
   카드구분(CDT)·직위(UT)는 운영에서 직접 등록하므로 시드하지 않는다 (공통코드관리) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('CS', N'카드상태', 'CS01', N'정상', 'Y'),
  ('CS', N'카드상태', 'CS02', N'분실', 'Y'),
  ('CS', N'카드상태', 'CS03', N'반납', 'Y'),
  ('CS', N'카드상태', 'CS04', N'정지', 'Y'),
  ('CS', N'카드상태', 'CS05', N'폐기', 'Y');

/* 발급구분(IS) — 카드 발급 사유 */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
  ('IS', N'발급구분', 'IS01', N'신규', 'Y'),
  ('IS', N'발급구분', 'IS02', N'재발급', 'Y'),
  ('IS', N'발급구분', 'IS03', N'분실재발급', 'Y');

/* 메뉴 (level 1 그룹은 menu_icon 지정 — 사이드바 아이콘) */
INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn) VALUES
  (200, N'정규인원관리', NULL, NULL,                 1, 1, 'card',     'Y'),
  (201, N'정규인원등록', 200,  '/person/person',      2, 1, NULL,      'Y'),
  (300, N'시스템관리',   NULL, NULL,                 1, 2, 'settings', 'Y'),
  (302, N'설정관리',     300,  '/system/system',     2, 1, NULL,       'Y'),
  (301, N'공통코드관리', 300,  '/system/common',     2, 2, NULL,       'Y'),
  (304, N'권한메뉴관리', 300,  '/system/menuAuth',   2, 3, NULL,       'Y'),
  (303, N'사용자관리',   300,  '/system/loginUser',  2, 4, NULL,       'Y'),
  (500, N'보안관리',     NULL, NULL,                 1, 3, 'guard',    'Y'),
  (501, N'감사추적',     500,  '/security/systemLog', 2, 1, NULL,      'Y'),
  (502, N'출입권한관리', 500,  '/security/acGroup',   2, 2, NULL,      'Y'),
  (600, N'차량관리',     NULL, NULL,                 1, 4, 'car',      'Y'),
  (601, N'차량등록관리', 600,  '/carInfo/car',        2, 1, NULL,      'Y'),
  (700, N'기관관리',     NULL, NULL,                 1, 5, 'company',  'Y'),
  (701, N'기관등록관리', 700,  '/company/company',    2, 1, NULL,      'Y'),
  (702, N'기관차량등록', 700,  '/company/car',        2, 2, NULL,      'Y'),
  (800, N'카드관리',     NULL, NULL,                 1, 6, 'card',     'Y'),
  (801, N'카드등록관리', 800,  '/card/card',          2, 1, NULL,      'Y');

/* 관리자 권한 + 공통코드관리 전권 + 관리자 계정 */
INSERT INTO dbo.tb_menu_auth (auth_name) VALUES (N'관리자');
DECLARE @authId int = SCOPE_IDENTITY();

INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
VALUES (@authId, 301, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 303, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 304, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 501, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 502, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 601, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 701, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 201, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 801, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 702, 'Y', 'Y', 'Y', 'Y');

/* 관리자 계정: 아이디 admin / 비밀번호 admin123 (ARIA 암호문) */
INSERT INTO dbo.tb_login_user
  (user_id, user_name, password, dept_name, use_yn, root_yn, auth_id, start_menu_id, work_location_code)
VALUES
  ('admin',
   '3F04A75824FA503043F56A4A78370B23',   -- ARIA('관리자')
   'CADF8C82EC0394005E5F4DA4520BBFE1',   -- ARIA('admin123')
   N'운영팀', 'Y', 'Y', @authId, 301, 'T1');

/* 조회전용 권한 + 계정: viewer / viewer123 — 메뉴 권한 CRUD 통제 확인용 (read Y, create/delete N) */
INSERT INTO dbo.tb_menu_auth (auth_name) VALUES (N'조회전용');
DECLARE @viewerAuthId int = SCOPE_IDENTITY();

INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
VALUES (@viewerAuthId, 301, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 303, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 304, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 501, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 502, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 601, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 701, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 201, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 801, 'Y', 'N', 'N', 'N'),
       (@viewerAuthId, 702, 'Y', 'N', 'N', 'N');

INSERT INTO dbo.tb_login_user
  (user_id, user_name, password, dept_name, use_yn, root_yn, auth_id, start_menu_id, work_location_code)
VALUES
  ('viewer',
   '90CC915CE3C405B614B53104CEECEB65',   -- ARIA('조회자')
   '61C20F44FC56313D845AD7B760D15F09',   -- ARIA('viewer123')
   N'운영팀', 'Y', 'N', @viewerAuthId, 301, 'T1');
