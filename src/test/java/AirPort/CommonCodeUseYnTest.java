package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.mapper.TbCommonMapper;
import AirPort.model.TbCommon;
import AirPort.service.AuditService;
import AirPort.service.CommonService;
import AirPort.service.MenuAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 공통코드 사용유무 규칙 단위 테스트 — 시스템 코드(user_input='N')는 업무 로직·화면이 그 코드의 존재를 전제하므로 미사용으로 돌릴 수 없다. 화면에서도 막지만
 * 서버가 최종 강제하는지 확인한다(클라이언트 값 불신).
 */
class CommonCodeUseYnTest {

  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);

  private CommonService service(String userInput) {
    TbCommon existing = code("VS", "VS01", "Y");
    existing.setUserInput(userInput);
    when(commonMapper.selectOne("VS", "VS01")).thenReturn(existing);
    when(commonMapper.update(any())).thenReturn(1);
    return new CommonService(commonMapper, auditService, menuAuthService);
  }

  private static TbCommon code(String cmmId, String codeId, String useYn) {
    TbCommon c = new TbCommon();
    c.setCmmId(cmmId);
    c.setCodeId(codeId);
    c.setCodeName("입실 중");
    c.setUseYn(useYn);
    return c;
  }

  private String savedUseYn() {
    ArgumentCaptor<TbCommon> saved = ArgumentCaptor.forClass(TbCommon.class);
    verify(commonMapper).update(saved.capture());
    return saved.getValue().getUseYn();
  }

  @Test
  void 시스템코드는_미사용으로_바꿔도_사용으로_강제된다() {
    service("N").update(code("VS", "VS01", "N"), null, 302);
    assertEquals("Y", savedUseYn(), "시스템 코드의 사용유무는 항상 '사용'");
  }

  @Test
  void 사용자코드는_요청한_사용유무가_그대로_저장된다() {
    service("Y").update(code("VS", "VS01", "N"), null, 302);
    assertEquals("N", savedUseYn(), "사용자 코드는 미사용으로 돌릴 수 있다");
  }
}
