package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.model.TbAcGroup;
import AirPort.model.TbCommon;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.model.VisitForm;
import AirPort.service.KioskVisitService;
import AirPort.service.VisitService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 키오스크(무인증) 방문 신청 — 로그인 없이 방문객이 직접 인솔자·방문구역·방문객·차량을 입력해 신청한다. (docs/security.md)
 *
 * <p>경로 {@code /kiosk/**} 는 WebConfig 에서 인증·메뉴통제 제외. 신청은 임시(PT02)·신청(VS01) tb_visit 로 저장되어 관리자
 * 임시인원등록 목록에 뜨고, 관리자가 확인 후 카드를 부여한다(BiostarX 연동은 그때).
 */
@Controller
@RequestMapping("/kiosk/visit")
public class KioskController {

  private final KioskVisitService kioskVisitService;

  public KioskController(KioskVisitService kioskVisitService) {
    this.kioskVisitService = kioskVisitService;
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
    return ApiResponse.ok(kioskVisitService.acGroupTree());
  }

  /** 인솔자 후보(정규인원) 검색 — 무인증. */
  @GetMapping("/managers")
  @ResponseBody
  public ApiResponse<List<TbPerson>> managers(@RequestParam(required = false) String keyword) {
    return ApiResponse.ok(kioskVisitService.searchManagers(keyword));
  }

  /** 공통코드(CAR 차량구역 / CT 차종) — 무인증, 허용 코드만. */
  @GetMapping("/codes")
  @ResponseBody
  public ApiResponse<List<TbCommon>> codes(@RequestParam String cmmId) {
    return ApiResponse.ok(kioskVisitService.codes(cmmId));
  }

  /** 방문 신청 저장 — 임시·신청 상태로 접수. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody VisitForm form) {
    kioskVisitService.create(form);
    return ApiResponse.okMessage("방문 신청이 접수되었습니다. 관리자 확인 후 카드가 발급됩니다.");
  }

  // ── [등록 수정] — 인솔자 인원ID·성명이 둘 다 맞아야 자기 신청(VS01)만 보고 고친다 ──

  /** 이 인솔자의 신청 상태 방문 목록. */
  @GetMapping("/mine")
  @ResponseBody
  public ApiResponse<List<TbVisit>> mine(
      @RequestParam String managerId, @RequestParam String managerName) {
    return ApiResponse.ok(kioskVisitService.applied(managerId, managerName));
  }

  /** 수정할 방문 상세 — 인솔자 확인을 다시 한다(방문번호만으로는 열 수 없다). */
  @GetMapping("/detail")
  @ResponseBody
  public ApiResponse<VisitService.VisitDetail> detail(
      @RequestParam int visitNo, @RequestParam String managerId, @RequestParam String managerName) {
    return ApiResponse.ok(kioskVisitService.detail(visitNo, managerId, managerName));
  }

  /** 방문 신청 수정 — 임시·신청 상태 유지. */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(
      @RequestBody VisitForm form,
      @RequestParam String managerId,
      @RequestParam String managerName) {
    kioskVisitService.update(form, managerId, managerName);
    return ApiResponse.okMessage("방문 신청이 수정되었습니다.");
  }
}
