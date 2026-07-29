package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbPersonFileMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPersonFile;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * 인원 증빙문서(회보근거·승인근거) 저장·조회. (docs/database.md tb_person_file)
 *
 * <p>업로드는 얼굴과 같은 방식으로 폼과 함께 BASE64 로 받는다(등록 시점에 인원이 아직 없어도 되고, 저장 트랜잭션이 한 번으로 끝난다). 파일명은 표시·목록용으로
 * {@code tb_person.id_check_file/approve_file} 에 비정규화해 함께 둔다.
 */
@Service
public class PersonFileService {

  /** 업로드 상한 — 폼 JSON 에 BASE64 로 실려오므로 과도한 본문을 막는다. */
  private static final int MAX_BYTES = 5 * 1024 * 1024;

  private final TbPersonFileMapper fileMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public PersonFileService(
      TbPersonFileMapper fileMapper, MenuAuthService menuAuthService, AuditService auditService) {
    this.fileMapper = fileMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  /**
   * 폼의 증빙문서 2종을 반영한다 — 등록/수정 공통.
   *
   * <p>규칙: 새 파일이 실려오면 교체, 파일명이 비어 있으면 삭제, 둘 다 아니면(기존 파일 유지) 손대지 않는다.
   */
  public void apply(PersonForm form) {
    save(
        form.getPersonId(),
        TbPersonFile.TYPE_ID_CHECK,
        form.getIdCheckFile(),
        form.getIdCheckFileData());
    save(
        form.getPersonId(),
        TbPersonFile.TYPE_APPROVE,
        form.getApproveFile(),
        form.getApproveFileData());
  }

  private void save(String personId, String fileType, String fileName, String base64) {
    if (fileName == null || fileName.isBlank()) {
      fileMapper.delete(personId, fileType); // 화면에서 문서를 지운 경우
      return;
    }
    if (base64 == null || base64.isBlank()) {
      return; // 기존 파일 유지 — 새로 올린 게 없다
    }
    byte[] data;
    try {
      data = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "첨부파일을 읽을 수 없습니다: " + fileName);
    }
    if (data.length > MAX_BYTES) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "첨부파일은 5MB 를 초과할 수 없습니다: " + fileName);
    }
    TbPersonFile file = new TbPersonFile();
    file.setPersonId(personId);
    file.setFileType(fileType);
    file.setFileName(fileName);
    file.setFileSize(data.length);
    file.setFileData(data);
    fileMapper.upsert(file);
  }

  /** 다운로드 — 파일 실체 조회(읽기 권한 + 감사). */
  public TbPersonFile download(
      String personId, String fileType, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbPersonFile file = fileMapper.selectOne(personId, fileType);
    if (file == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "등록된 첨부파일이 없습니다.");
    }
    auditService.log(
        actor, AuditService.READ, menuId, "증빙문서 다운로드: " + personId + " / " + file.getFileName());
    return file;
  }
}
