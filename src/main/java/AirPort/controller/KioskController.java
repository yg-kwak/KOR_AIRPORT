package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.model.TbAcGroup;
import AirPort.model.TbPerson;
import AirPort.model.VisitForm;
import AirPort.service.VisitService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 키오스크(무인증) 방문 신청 — 로그인 없이 방문객이 직접 인솔자·방문구역·방문객을 입력해 신청한다. (docs/security.md)
 *
 * <p>경로 {@code /kiosk/**} 는 WebConfig 에서 인증·메뉴통제 제외. 신청은 임시(PT02)·신청(VS01) 상태의 tb_visit
 * 로 저장되어 관리자 임시인원등록 목록에 뜨고, 관리자가 확인 후 카드를 부여한다(BiostarX 연동은 그때).
 */
@Controller
@RequestMapping("/kiosk/visit")
public class KioskController {

  private final VisitService visitService;

  public KioskController(VisitService visitService) {
    this.visitService = visitService;
  }

  /** 방문 등록 화면. */
  @GetMapping
  public String page() {
    return "kiosk/visit";
  }

  /** 방문구역(사용자 출입그룹) 트리 — 무인증. */
  @GetMapping("/acGroups")
  @ResponseBody
  public ApiResponse<List<TbAcGroup>> acGroups() {
    return ApiResponse.ok(visitService.acGroupTreeKiosk());
  }

  /** 인솔자 후보(정규인원) 검색 — 무인증. */
  @GetMapping("/managers")
  @ResponseBody
  public ApiResponse<List<TbPerson>> managers(@RequestParam(required = false) String keyword) {
    return ApiResponse.ok(visitService.searchManagersPublic(keyword));
  }

  /** 방문 신청 저장 — 임시·신청 상태로 접수. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody VisitForm form) {
    visitService.createFromKiosk(form);
    return ApiResponse.okMessage("방문 신청이 접수되었습니다. 관리자 확인 후 카드가 발급됩니다.");
  }
}
