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

/* 메뉴 (level 1 그룹은 menu_icon 지정 — 사이드바 아이콘) */
INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn) VALUES
  (300, N'시스템관리',   NULL, NULL,                 1, 1, 'settings', 'Y'),
  (301, N'공통코드관리', 300,  '/system/commonCode', 2, 1, NULL,       'Y'),
  (302, N'설정관리',     300,  '/system/system',     2, 2, NULL,       'Y'),
  (303, N'사용자관리',   300,  '/system/user',       2, 3, NULL,       'Y');

/* 관리자 권한 + 공통코드관리 전권 + 관리자 계정 */
INSERT INTO dbo.tb_menu_auth (auth_name) VALUES (N'관리자');
DECLARE @authId int = SCOPE_IDENTITY();

INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
VALUES (@authId, 301, 'Y', 'Y', 'Y', 'Y'),
       (@authId, 303, 'Y', 'Y', 'Y', 'Y');

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
       (@viewerAuthId, 303, 'Y', 'N', 'N', 'N');

INSERT INTO dbo.tb_login_user
  (user_id, user_name, password, dept_name, use_yn, root_yn, auth_id, start_menu_id, work_location_code)
VALUES
  ('viewer',
   '90CC915CE3C405B614B53104CEECEB65',   -- ARIA('조회자')
   '61C20F44FC56313D845AD7B760D15F09',   -- ARIA('viewer123')
   N'운영팀', 'Y', 'N', @viewerAuthId, 301, 'T1');
