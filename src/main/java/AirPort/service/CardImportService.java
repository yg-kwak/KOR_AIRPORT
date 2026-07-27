package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.model.ExcelImportResult;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.util.ExcelUtil;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 카드 엑셀 일괄등록 — 행마다 {@link CardService#createCard}를 호출한다(행 단위 독립 트랜잭션이라 한 행 실패가 나머지를 막지 않는다). 카드번호
 * 중복·필수값·BiostarX 등록 등 검증은 createCard 규칙을 그대로 재사용하고, 실패 행은 사유와 함께 결과에 담는다. 일괄등록 카드는 미발급(인원·차량 미할당)
 * 상태로 들어간다. (docs/backend.md)
 */
@Service
public class CardImportService {

  /** 양식 헤더(열 순서와 1:1). 별표는 필수. 코드값(카드구분·패스구분·카드상태)은 공통코드 ID 로 입력한다. */
  public static final String[] IMPORT_HEADERS = {
    "카드번호*", "카드구분*", "패스구분", "카드명칭*", "카드상태", "발급사유", "메모"
  };

  /** 카드상태 기본값 — tb_common(CS) 정상. 비면 정상으로 등록한다. */
  private static final String DEFAULT_STATUS = "CS01";

  private final CardService cardService;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public CardImportService(
      CardService cardService, MenuAuthService menuAuthService, AuditService auditService) {
    this.cardService = cardService;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 엑셀 일괄등록 — 성공/실패 건수와 행별 사유. 각 행은 cardService.createCard(프록시)로 독립 트랜잭션 처리. */
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
      try {
        cardService.createCard(toRow(r), actor, menuId); // 프록시 경유 — 행마다 독립 트랜잭션
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
        "카드 엑셀 일괄등록 (성공 " + result.getSuccess() + " / 실패 " + result.getFail() + ")");
    return result;
  }

  /** 엑셀 한 행 → TbCard. 카드상태 비면 기본값(정상). 발급 대상(인원·차량)은 다루지 않는다(미발급으로 등록). */
  private TbCard toRow(String[] r) {
    TbCard card = new TbCard();
    card.setBiostarCardValue(blankToNull(r[0]));
    card.setCardType(blankToNull(r[1]));
    card.setPassType(blankToNull(r[2]));
    card.setCardName(blankToNull(r[3]));
    card.setCardStatus(orDefault(r[4], DEFAULT_STATUS));
    card.setIssueReason(blankToNull(r[5]));
    card.setRemark(blankToNull(r[6]));
    return card;
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  private static String orDefault(String v, String dflt) {
    return (v == null || v.isBlank()) ? dflt : v.trim();
  }
}
