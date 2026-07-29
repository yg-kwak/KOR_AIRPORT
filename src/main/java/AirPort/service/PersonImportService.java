package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.ExcelImportResult;
import AirPort.model.PersonForm;
import AirPort.model.TbLoginUser;
import AirPort.util.ExcelUtil;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 정규인원 엑셀 일괄등록 — 행마다 {@link PersonService#create}를 호출한다(행 단위 독립 트랜잭션이라 한 행 실패가 나머지를 막지 않는다).
 * <b>사용자권한·카드정보·얼굴은 제외</b>(엑셀로 다루지 않는다). 나머지 검증·ARIA 암호화·BiostarX 동기화는 create 규칙을 그대로 재사용한다.
 * (docs/backend.md)
 */
@Service
public class PersonImportService {

  /** 양식 헤더(열 순서와 1:1). 별표는 필수. 인원ID 비면 자동 채번, 상태/출입기간 비면 기본값. */
  public static final String[] IMPORT_HEADERS = {
    "기관코드*", "인원ID", "성명*", "생년월일", "연락처", "직위코드", "상태코드", "출입시작일", "출입종료일", "주요업무", "메모"
  };

  /** 양식 2행에 넣는 예시 행 — 그대로 두거나 지우면 등록에서 건너뛴다(사용자가 덮어쓰면 정상 등록). */
  public static final String[] EXAMPLE_ROW = {
    "C001",
    "",
    "홍길동",
    "1990-01-01",
    "010-1234-5678",
    "",
    "01",
    "2026-01-01T09:00",
    "2026-12-31T18:00",
    "출입관리",
    "예시 행 — 지우거나 덮어써서 입력하세요"
  };

  /** 상태 기본값 — tb_common(PS) 신규. */
  private static final String DEFAULT_STATUS = "01";

  /** 출입종료일 기본값 — BiostarX expiry 상한. */
  private static final String DEFAULT_ACCESS_END = "2037-12-31T23:59";

  private final PersonService personService;
  private final TbPersonMapper personMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public PersonImportService(
      PersonService personService,
      TbPersonMapper personMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.personService = personService;
    this.personMapper = personMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 엑셀 일괄등록 — 성공/실패 건수와 행별 사유. 각 행은 personService.create(프록시)로 독립 트랜잭션 처리. */
  public ExcelImportResult importExcel(InputStream in, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    ExcelImportResult result = new ExcelImportResult();
    List<String[]> rows;
    try {
      rows = ExcelUtil.read(in, IMPORT_HEADERS.length);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀을 읽을 수 없습니다. 양식 파일을 확인하세요.");
    }
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "등록할 데이터가 없습니다. 2행부터 입력하세요.");
    }
    int line = 1; // 헤더가 1행 → 데이터는 2행부터
    for (String[] r : rows) {
      line++;
      if (java.util.Arrays.equals(r, EXAMPLE_ROW)) {
        continue; // 안내용 예시 행 — 건너뛴다
      }
      try {
        personService.create(toForm(r), actor, menuId); // 프록시 경유 — 행마다 독립 트랜잭션
        result.addSuccess();
      } catch (BusinessException e) {
        result.addError(line, e.getMessage());
      } catch (Exception e) {
        result.addError(line, "처리 실패");
      }
    }
    auditService.log(
        actor,
        AuditService.CREATE,
        menuId,
        "정규인원 엑셀 일괄등록 (성공 " + result.getSuccess() + " / 실패 " + result.getFail() + ")");
    return result;
  }

  /** 엑셀 한 행 → PersonForm. 인원ID 비면 자동 채번, 상태/출입기간 비면 기본값(사용자권한·카드는 다루지 않음). */
  private PersonForm toForm(String[] r) {
    PersonForm form = new PersonForm();
    String personId = blankToNull(r[1]);
    form.setPersonId(personId != null ? personId : personMapper.selectNextPersonId());
    form.setCompanyCode(blankToNull(r[0]));
    form.setPersonName(blankToNull(r[2]));
    form.setBirthDate(blankToNull(r[3]));
    form.setPersonPhone(blankToNull(r[4]));
    form.setTitleCode(blankToNull(r[5]));
    form.setStatusCode(orDefault(r[6], DEFAULT_STATUS));
    form.setAccessStartDt(orDefault(r[7], LocalDate.now().toString()));
    form.setAccessEndDt(orDefault(r[8], DEFAULT_ACCESS_END));
    form.setMainTask(blankToNull(r[9]));
    form.setRemark(blankToNull(r[10]));
    return form;
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  private static String orDefault(String v, String dflt) {
    return (v == null || v.isBlank()) ? dflt : v.trim();
  }
}
