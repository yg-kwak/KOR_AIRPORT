package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
import AirPort.model.TbCommon;
import org.springframework.stereotype.Component;

/**
 * 공통코드 값 검증 — 저장 전에 {@code tb_common} 에 <b>실제로 있는 코드인지</b> 확인한다. (docs/database.md)
 *
 * <p>화면은 코드 선택 팝업이라 잘못된 값이 오기 어렵지만, <b>엑셀 일괄등록은 사용자가 코드ID 를 직접 적는다</b>. 검증이 없으면 오타(예: {@code
 * CDT99})가 그대로 저장돼 목록의 구분·상태 칸이 빈 채로 남고, 나중에 원인을 찾기 어렵다. 서비스 저장 경로에서 막아 화면·엑셀 어느 쪽으로 들어와도 같은 규칙이
 * 적용되게 한다.
 */
@Component
public class CodeValidationService {

  private final TbCommonMapper commonMapper;

  public CodeValidationService(TbCommonMapper commonMapper) {
    this.commonMapper = commonMapper;
  }

  /**
   * 코드값 검증 — 값이 비면 통과(필수 여부는 각 서비스가 따로 본다), 있으면 존재·사용 여부를 확인한다.
   *
   * @param cmmId 코드구분 ID (예: CDT)
   * @param codeId 검증할 코드 ID (예: CDT01)
   * @param label 사용자에게 보일 항목명 (예: 카드구분)
   * @throws BusinessException 없는 코드이거나 사용중지된 코드일 때 — 무엇을 고쳐야 하는지 메시지에 담는다
   */
  public void validate(String cmmId, String codeId, String label) {
    validate(cmmId, codeId, label, null);
  }

  /**
   * 수정용 검증 — <b>값이 바뀐 경우에만</b> 확인한다. 저장된 값과 같으면 통과.
   *
   * <p>화면은 값을 안 건드려도 폼 전체를 되전송한다. 운영에서 코드를 삭제하거나 사용중지하면, 그 코드를 쓰던 기존 행은 코드와 무관한 항목(메모 등)만 고치려 해도
   * 저장이 막힌다. 이미 저장돼 있던 값은 그대로 통과시켜 <b>기존 데이터가 인질이 되지 않게</b> 한다(새로 넣는 값만 막는다).
   *
   * @param prevCodeId 저장돼 있던 값 — 같으면 검증을 건너뛴다
   */
  public void validate(String cmmId, String codeId, String label, String prevCodeId) {
    if (codeId == null || codeId.isBlank()) {
      return;
    }
    if (prevCodeId != null && prevCodeId.equals(codeId)) {
      return; // 바뀌지 않은 값 — 지금 유효하지 않더라도 기존 행 수정을 막지 않는다
    }
    TbCommon code = commonMapper.selectOne(cmmId, codeId.trim());
    if (code == null) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          label + " 코드 '" + codeId.trim() + "' 가 없습니다. 공통코드관리(" + cmmId + ")에 등록된 코드ID로 입력하세요.");
    }
    if (!"Y".equals(code.getUseYn())) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          label + " 코드 '" + codeId.trim() + "'(" + code.getCodeName() + ") 는 사용중지된 코드입니다.");
    }
  }
}
