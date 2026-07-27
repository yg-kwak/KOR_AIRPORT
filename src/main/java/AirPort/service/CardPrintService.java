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

  /** 인쇄 — 앞/뒤 카드를 프린터로 출력. */
  public void print(String personId, int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    Ctx ctx = prepare(personId, cardId);
    try {
      adapter.print(renderSides(ctx));
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INTERNAL, "카드 인쇄 실패: " + e.getMessage());
    }
    auditService.log(actor, AuditService.CREATE, menuId, "카드 프린트 출력: " + personId + " / 카드 " + cardId);
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
}
