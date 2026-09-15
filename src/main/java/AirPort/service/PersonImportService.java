package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.ExcelImportResult;
import AirPort.model.PersonForm;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.util.ExcelUtil;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 정규인원 엑셀 일괄등록 — 행마다 {@link PersonService#create}를 호출한다(행 단위 독립 트랜잭션이라 한 행 실패가 나머지를 막지 않는다). [기존 인원
 * 갱신] 을 켜면 이미 있는 인원ID 행은 {@link PersonImportUpdateService} 로 보내 엑셀 열만 갱신한다. <b>사용자권한·카드정보·얼굴은
 * 제외</b>(엑셀로 다루지 않는다). 나머지 검증·ARIA 암호화·BiostarX 동기화는 create 규칙을 그대로 재사용한다. (docs/backend.md)
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
  private final PersonImportUpdateService updateService;
  private final TbPersonMapper personMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public PersonImportService(
      PersonService personService,
      PersonImportUpdateService updateService,
      TbPersonMapper personMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.personService = personService;
    this.updateService = updateService;
    this.personMapper = personMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /**
   * 엑셀 일괄등록 — 성공/실패 건수와 행별 사유. 각 행은 프록시 경유로 독립 트랜잭션 처리.
   *
   * @param updateExisting [기존 인원 갱신] — 켜면 이미 있는 인원ID 행은 <b>엑셀에 값이 있는 열만</b> 갱신한다(빈 칸은 그대로). 끄면 지금처럼
   *     "이미 존재하는 인원ID" 로 실패한다 — 명단을 실수로 덮어쓰지 않게 기본은 끔이다.
   */
  public ExcelImportResult importExcel(
      InputStream in, boolean updateExisting, TbLoginUser actor, Integer menuId) {
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
        if (updateExisting && exists(r[1])) {
          updateService.update(toForm(r, true), actor, menuId); // 프록시 경유 — 행마다 독립 트랜잭션
          result.addUpdated();
        } else {
          personService.create(toForm(r, false), actor, menuId);
          result.addSuccess();
        }
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
        "정규인원 엑셀 일괄등록 (신규 "
            + result.getSuccess()
            + " / 갱신 "
            + result.getUpdated()
            + " / 실패 "
            + result.getFail()
            + (updateExisting ? ", 기존 인원 갱신 켬" : "")
            + ")");
    return result;
  }

  /** 살아 있는 인원ID 인가 — 갱신 대상 판정. 삭제된 ID 는 create 가 되살리는 길로 보낸다. */
  private boolean exists(String personId) {
    String id = blankToNull(personId);
    if (id == null) {
      return false;
    }
    TbPerson p = personMapper.selectById(id.trim());
    return p != null && !"Y".equals(p.getDelYn());
  }

  /**
   * 엑셀 한 행 → PersonForm. 인원ID 비면 자동 채번(사용자권한·카드는 다루지 않음).
   *
   * <p>신규는 상태/출입기간이 비면 기본값을 넣는다. <b>갱신은 빈 칸을 null 로 둔다</b> — 그래야 "빈 칸은 그대로" 가 지켜진다(기본값을 넣으면 비워 둔 상태
   * 칸이 '신규' 로, 기간 칸이 오늘~2037 로 바뀐다).
   */
  private PersonForm toForm(String[] r, boolean forUpdate) {
    PersonForm form = new PersonForm();
    String personId = blankToNull(r[1]);
    form.setPersonId(personId != null ? personId.trim() : personMapper.selectNextPersonId());
    form.setCompanyCode(blankToNull(r[0]));
    form.setPersonName(blankToNull(r[2]));
    form.setBirthDate(blankToNull(r[3]));
    form.setPersonPhone(blankToNull(r[4]));
    form.setTitleCode(blankToNull(r[5]));
    form.setStatusCode(forUpdate ? blankToNull(r[6]) : orDefault(r[6], DEFAULT_STATUS));
    form.setAccessStartDt(
        forUpdate ? blankToNull(r[7]) : orDefault(r[7], LocalDate.now().toString()));
    form.setAccessEndDt(forUpdate ? blankToNull(r[8]) : orDefault(r[8], DEFAULT_ACCESS_END));
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
