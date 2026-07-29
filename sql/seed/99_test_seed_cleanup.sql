/* 테스트용 대량 시드 삭제 — sql/seed/99_test_seed.sql 로 넣은 데이터만 지운다(TST 접두/9xxx 번호). */
SET NOCOUNT ON;
DELETE FROM dbo.tb_visit_manager WHERE person_id LIKE 'TSTP%' OR visit_no IN (SELECT visit_no FROM dbo.tb_visit WHERE company_name LIKE N'테스트방문업체%');
DELETE FROM dbo.tb_visit_person  WHERE person_id LIKE 'TSTV%' OR visit_no IN (SELECT visit_no FROM dbo.tb_visit WHERE company_name LIKE N'테스트방문업체%');
DELETE FROM dbo.tb_visit_car     WHERE visit_no IN (SELECT visit_no FROM dbo.tb_visit WHERE company_name LIKE N'테스트방문업체%');
DELETE FROM dbo.tb_visit_ac_group WHERE visit_no IN (SELECT visit_no FROM dbo.tb_visit WHERE company_name LIKE N'테스트방문업체%');
DELETE FROM dbo.tb_visit_car_ac_group WHERE visit_no IN (SELECT visit_no FROM dbo.tb_visit WHERE company_name LIKE N'테스트방문업체%');
DELETE FROM dbo.tb_visit         WHERE company_name LIKE N'테스트방문업체%';
DELETE FROM dbo.tb_card          WHERE biostar_card_value LIKE '90000%' OR card_name LIKE N'테스트카드%';
DELETE FROM dbo.tb_car_ac_group  WHERE car_id IN (SELECT car_id FROM dbo.tb_car WHERE car_name LIKE N'테스트차량%');
DELETE FROM dbo.tb_car           WHERE car_name LIKE N'테스트차량%';
DELETE FROM dbo.tb_person_ac_group WHERE person_id LIKE 'TSTP%' OR person_id LIKE 'TSTV%';
DELETE FROM dbo.tb_person_photo  WHERE person_id LIKE 'TSTP%' OR person_id LIKE 'TSTV%';
DELETE FROM dbo.tb_person        WHERE person_id LIKE 'TSTP%' OR person_id LIKE 'TSTV%';
DELETE FROM dbo.tb_company       WHERE company_code LIKE 'TSTC%';
PRINT '테스트 시드 삭제 완료';
