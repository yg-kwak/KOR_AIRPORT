package AirPort.service;

import AirPort.adapter.cardprint.CardPrintAdapter;
import AirPort.adapter.cardprint.CardPrintRenderer;
import AirPort.adapter.cardprint.CardProject;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 카드 프린트 오케스트레이션 — 인원/카드/얼굴로 카드 이미지를 렌더하고 미리보기·인쇄한다. (docs/backend.md)
 *
 * <p>얼굴(tb_person_photo)과 카드가 모두 있는 인원만 출력 가능. 템플릿의 {@code {컬럼}} 바인딩에 인원 데이터를 매핑한다:
 * {이름}=성명, {회사명}=기관명, {구역}=권한 최상위 구역번호, {발급번호}="발급번호 : "+카드명칭, {발급일}=오늘.
 * 외부 프린터 접근은 {@link CardPrintAdapter} 로만.
 */
@Service
public class CardPrintService {

  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbCardMapper cardMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final CardPrintRenderer renderer;
  private final CardPrintAdapter adapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  @Value("${card-print.canvas-width:540}")
  private double canvasWidth;

  public CardPrintService(
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbCardMapper cardMapper,
      TbPersonAcGroupMapper acGroupMapper,
      CardPrintRenderer renderer,
      CardPrintAdapter adapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.cardMapper = cardMapper;
    this.acGroupMapper = acGroupMapper;
    this.renderer = renderer;
    this.adapter = adapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 미리보기 — 앞/뒤 카드 이미지를 data URL 로 반환(인쇄 안 함). */
  public List<String> preview(String personId, int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    Ctx ctx = prepare(personId, cardId);
    List<String> images = new ArrayList<>();
    for (BufferedImage img : renderSides(ctx)) {
      images.add(renderer.toDataUrl(img));
    }
    auditService.log(actor, AuditService.READ, menuId, "카드 프린트 미리보기: " + personId);
    return images;
  }

  /** 인쇄 확정(감사) — 실제 인쇄는 클라이언트 브라우저가 미리보기 이미지를 window.print() 로 출력한다(프린터가 클라이언트 PC). */
  public void print(String personId, int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    prepare(personId, cardId); // 대상 유효성(얼굴·카드) 재검증
    auditService.log(actor, AuditService.CREATE, menuId, "카드 프린트 출력: " + personId + " / 카드 " + cardId);
  }

  /** 일괄 출력 사전 점검 — 출력하지 않고 대상 명단 + 문제(2장이상/카드없음/얼굴없음) 분류만 반환. */
  public BulkCheck checkBulk(List<String> personIds, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    BulkCheck r = new BulkCheck();
    if (personIds == null) {
      return r;
    }
    for (String pid : personIds) {
      List<TbCard> cards = cardMapper.selectByPerson(pid);
      if (cards.size() >= 2) {
        r.multi.add(pid);
      } else if (cards.isEmpty() || cards.get(0).getBiostarCardId() == null) {
        r.noCard.add(pid);
      } else if (isBlank(photoMapper.selectPhoto(pid))) {
        r.noFace.add(pid);
      } else {
        TbPerson p = personMapper.selectById(pid);
        Target t = new Target();
        t.personId = pid;
        t.personName = p != null ? decrypt(p.getPersonName()) : "";
        t.cardName = cards.get(0).getCardName();
        r.targets.add(t);
      }
    }
    return r;
  }

  /**
   * 일괄 출력 — 선택 인원 전량 검증 후 각자 카드 출력. 카드 1장·얼굴 보유자만 대상.
   *
   * <p>카드 2장 이상 보유자가 있으면 아무것도 출력하지 않고 해당 인원ID를 알려주며 반환한다.
   */
  public List<String> printBulk(List<String> personIds, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (personIds == null || personIds.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "선택된 인원이 없습니다.");
    }
    List<String> multi = new ArrayList<>();
    List<String> noCard = new ArrayList<>();
    List<String> noFace = new ArrayList<>();
    Map<String, Integer> targets = new LinkedHashMap<>();
    for (String pid : personIds) {
      List<TbCard> cards = cardMapper.selectByPerson(pid);
      if (cards.size() >= 2) {
        multi.add(pid);
      } else if (cards.isEmpty() || cards.get(0).getBiostarCardId() == null) {
        noCard.add(pid);
      } else if (isBlank(photoMapper.selectPhoto(pid))) {
        noFace.add(pid);
      } else {
        targets.put(pid, cards.get(0).getCardId());
      }
    }
    // 전량 검증(하나라도 문제면 출력하지 않음)
    reject(multi, "2장 이상의 카드를 보유한 사용자가 있습니다");
    reject(noCard, "카드가 없는 사용자가 있습니다");
    reject(noFace, "얼굴이 없는 사용자가 있습니다");
    // 실제 인쇄는 클라이언트가 하므로 대상 전원의 앞/뒤 이미지를 순서대로 모아 반환한다
    List<String> images = new ArrayList<>();
    for (Map.Entry<String, Integer> e : targets.entrySet()) {
      Ctx ctx = prepare(e.getKey(), e.getValue());
      for (BufferedImage img : renderSides(ctx)) {
        images.add(renderer.toDataUrl(img));
      }
    }
    auditService.log(actor, AuditService.CREATE, menuId, "카드 프린트 일괄 출력: " + targets.size() + "건");
    return images;
  }

  private static void reject(List<String> ids, String reason) {
    if (!ids.isEmpty()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, reason + "( 인원ID : " + String.join(", ", ids) + " )");
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /** 인원·카드·얼굴 조회 + 검증(얼굴·카드 모두 필수). */
  private Ctx prepare(String personId, int cardId) {
    TbPerson p = personMapper.selectById(personId);
    if (p == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "인원을 찾을 수 없습니다.");
    }
    TbCard card = cardMapper.selectById(cardId);
    if (card == null || card.getBiostarCardId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "등록된 카드가 있는 인원만 출력할 수 있습니다.");
    }
    String face = photoMapper.selectPhoto(personId);
    if (face == null || face.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "얼굴이 등록된 인원만 출력할 수 있습니다.");
    }
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("이름", decrypt(p.getPersonName()));
    fields.put("회사명", nullToEmpty(p.getCompanyName()));
    fields.put("구역", areaNumbers(personId)); // 선택 권한의 최상위 구역 번호(예 1234, 12)
    fields.put("발급번호", "발급번호 : " + nullToEmpty(card.getCardName()));
    fields.put("발급일", LocalDate.now().toString());
    Ctx ctx = new Ctx();
    ctx.fields = fields;
    ctx.face = face;
    return ctx;
  }

  /** 인원 권한의 최상위 구역 번호를 오름차순으로 이어붙인다. ar_code(AR01..)에서 숫자만 추출. */
  private String areaNumbers(String personId) {
    TreeSet<Integer> nums = new TreeSet<>();
    for (String arCode : acGroupMapper.selectAreaCodes(personId)) {
      String digits = arCode == null ? "" : arCode.replaceAll("[^0-9]", "");
      if (!digits.isEmpty()) {
        nums.add(Integer.parseInt(digits));
      }
    }
    StringBuilder sb = new StringBuilder();
    for (Integer n : nums) {
      sb.append(n);
    }
    return sb.toString();
  }

  /** printSides 에 따라 앞(+뒤) 면을 렌더. */
  private List<BufferedImage> renderSides(Ctx ctx) {
    CardProject project = adapter.project();
    if (project.cardData == null || project.cardData.front == null) {
      throw new BusinessException(ErrorCode.INTERNAL, "카드 템플릿에 앞면이 없습니다.");
    }
    List<BufferedImage> sides = new ArrayList<>();
    sides.add(renderer.render(project.cardData.front, ctx.fields, ctx.face, canvasWidth));
    boolean both = "both".equalsIgnoreCase(project.settings.printSides);
    if (both && project.cardData.back != null) {
      sides.add(renderer.render(project.cardData.back, ctx.fields, null, canvasWidth));
    }
    return sides;
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? "" : ARIAUtil.ariaDecrypt(cipher);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static class Ctx {
    Map<String, String> fields;
    String face;
  }

  /** 일괄 출력 사전 점검 결과 — 대상 명단 + 문제 인원ID 분류. */
  public static class BulkCheck {
    public List<Target> targets = new ArrayList<>();
    public List<String> multi = new ArrayList<>(); // 카드 2장 이상
    public List<String> noCard = new ArrayList<>(); // 카드 없음
    public List<String> noFace = new ArrayList<>(); // 얼굴 없음

    /** 문제 인원이 없고 대상이 1명 이상이면 출력 가능. */
    public boolean printable() {
      return multi.isEmpty() && noCard.isEmpty() && noFace.isEmpty() && !targets.isEmpty();
    }
  }

  /** 출력 대상 1명 — 화면 명단 표시용. */
  public static class Target {
    public String personId;
    public String personName;
    public String cardName;
  }
}
