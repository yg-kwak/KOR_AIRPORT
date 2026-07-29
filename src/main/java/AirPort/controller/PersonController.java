package AirPort.controller;

import AirPort.adapter.BiostarCard;
import AirPort.adapter.BiostarFace;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.ExcelImportResult;
import AirPort.model.MenuPermission;
import AirPort.model.PersonForm;
import AirPort.model.PersonSearchParam;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbPersonFile;
import AirPort.service.AcGroupService;
import AirPort.service.CardPrintService;
import AirPort.service.CardService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.PersonFaceService;
import AirPort.service.PersonFileService;
import AirPort.service.PersonImportService;
import AirPort.service.PersonService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 정규인원등록 (tb_person, person_type='PT01') — 목록/등록 + 얼굴(업로드·촬영) 프록시.
 *
 * <p>얼굴 API 는 브라우저가 BiostarX 를 직접 부를 수 없으므로 서버가 중계한다(외부 연동은 adapter 로만, AGENTS §4).
 */
@Controller
@RequestMapping("/person/person")
public class PersonController {

  private final PersonService personService;
  private final PersonFileService personFileService;
  private final PersonImportService personImportService;
  private final PersonFaceService personFaceService;
  private final CardService cardService;
  private final CardPrintService cardPrintService;
  private final AcGroupService acGroupService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public PersonController(
      PersonService personService,
      PersonFileService personFileService,
      PersonImportService personImportService,
      PersonFaceService personFaceService,
      CardService cardService,
      CardPrintService cardPrintService,
      AcGroupService acGroupService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.personService = personService;
    this.personFileService = personFileService;
    this.personImportService = personImportService;
    this.personFaceService = personFaceService;
    this.cardService = cardService;
    this.cardPrintService = cardPrintService;
    this.acGroupService = acGroupService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/person/person";
  }

  /** 다음 인원ID (AJAX) — 등록 모달 초기값 */
  @GetMapping("/nextId")
  @ResponseBody
  public ApiResponse<String> nextId(HttpSession session) {
    return ApiResponse.ok(personService.nextPersonId(actor(session), menuId()));
  }

  /** 엑셀 일괄등록 양식 다운로드 — 헤더 + 예시 1행(기관코드*·성명* 필수). 예시 행은 등록 시 건너뛴다. */
  @GetMapping("/excel/template")
  public void excelTemplate(HttpServletResponse response) throws java.io.IOException {
    AirPort.util.ExcelUtil.download(
        response,
        "정규인원등록양식.xlsx",
        PersonImportService.IMPORT_HEADERS,
        java.util.List.<String[]>of(PersonImportService.EXAMPLE_ROW));
  }

  /** 엑셀 일괄등록 (AJAX, multipart) — 사용자권한·카드정보 제외. 행별 성공/실패 요약. */
  @PostMapping("/excel/import")
  @ResponseBody
  public ApiResponse<ExcelImportResult> excelImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
      HttpSession session)
      throws java.io.IOException {
    if (file == null || file.isEmpty()) {
      return ApiResponse.fail(
          AirPort.common.exception.ErrorCode.INVALID_INPUT.code(), "업로드할 파일을 선택하세요.");
    }
    return ApiResponse.ok(
        personImportService.importExcel(file.getInputStream(), actor(session), menuId()));
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbPerson>> list(PersonSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(personService.list(param, actor(session), menuId()));
  }

  /** 출입권한 선택 트리 (AJAX) — tb_ac_group 재사용 */
  @GetMapping("/acGroups")
  @ResponseBody
  public ApiResponse<List<AirPort.model.TbAcGroup>> acGroups(HttpSession session) {
    return ApiResponse.ok(acGroupService.tree(actor(session), menuId()));
  }

  /** 인원의 출입권한 ID 목록 (AJAX) */
  @GetMapping("/personAcGroups")
  @ResponseBody
  public ApiResponse<List<Integer>> personAcGroups(
      @RequestParam String personId, HttpSession session) {
    return ApiResponse.ok(personService.acGroupIds(personId, actor(session), menuId()));
  }

  /** 사진 파일 업로드 → BiostarX 정규화 얼굴 (AJAX) */
  @PostMapping("/face/upload")
  @ResponseBody
  public ApiResponse<BiostarFace> faceUpload(
      @RequestBody Map<String, String> body, HttpSession session) {
    return ApiResponse.ok(
        personFaceService.uploadPicture(body.get("image"), actor(session), menuId()));
  }

  /** 로그인 계정의 장치로 얼굴 촬영 (AJAX) */
  @GetMapping("/face/capture")
  @ResponseBody
  public ApiResponse<BiostarFace> faceCapture(HttpSession session) {
    return ApiResponse.ok(personFaceService.captureFace(actor(session), menuId()));
  }

  /** 인원 등록사진 (AJAX) — 수정 모달에서 기존 얼굴 표시용 */
  @GetMapping("/photo")
  @ResponseBody
  public ApiResponse<String> photo(@RequestParam String personId, HttpSession session) {
    return ApiResponse.ok(personService.photo(personId, actor(session), menuId()));
  }

  /** 등록 (AJAX) — BiostarX 사용자 생성 실패 시에도 인원은 저장하고 경고를 덧붙인다. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody PersonForm form, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("등록되었습니다.", personService.create(form, actor(session), menuId())));
  }

  /** 수정 (AJAX) — 변경분만 BiostarX 로 동기화 */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody PersonForm form, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("수정되었습니다.", personService.update(form, actor(session), menuId())));
  }

  /** 인원의 카드 목록 (AJAX) — 수정 모달의 카드정보 탭 */
  @GetMapping("/cards")
  @ResponseBody
  public ApiResponse<List<TbCard>> cards(@RequestParam String personId, HttpSession session) {
    return ApiResponse.ok(cardService.listByPerson(personId, actor(session), menuId()));
  }

  /** 미할당 카드 목록 (AJAX) — 카드 할당하기 팝업 */
  @GetMapping("/card/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCard>> cardUnassigned(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(cardService.listUnassigned(keyword, actor(session), menuId()));
  }

  /** 장치 리더로 카드번호 읽기 (AJAX) — 로그인 계정의 장치(dev_id) */
  @PostMapping("/card/scan")
  @ResponseBody
  public ApiResponse<BiostarCard> cardScan(HttpSession session) {
    return ApiResponse.ok(cardService.scan(actor(session), menuId()));
  }

  /** BiostarX 카드 등록 (AJAX) — 카드 추가 시 즉시 호출된다(정책) */
  @PostMapping("/card/register")
  @ResponseBody
  public ApiResponse<BiostarCard> cardRegister(
      @RequestBody Map<String, String> body, HttpSession session) {
    return ApiResponse.ok(cardService.register(body.get("cardNo"), actor(session), menuId()));
  }

  /** 카드 프린트 미리보기 — 얼굴·카드 등록 인원의 앞/뒤 카드 이미지(data URL) */
  @PostMapping("/card/print/preview")
  @ResponseBody
  public ApiResponse<List<String>> cardPrintPreview(
      @RequestBody CardPrintReq req, HttpSession session) {
    return ApiResponse.ok(
        cardPrintService.preview(req.personId, req.cardId, actor(session), menuId()));
  }

  /** 카드 프린트 출력 — 카드 프린터로 인쇄 */
  @PostMapping("/card/print")
  @ResponseBody
  public ApiResponse<Void> cardPrint(@RequestBody CardPrintReq req, HttpSession session) {
    cardPrintService.print(req.personId, req.cardId, actor(session), menuId());
    return ApiResponse.okMessage("인쇄를 요청했습니다.");
  }

  /** 카드 일괄 출력 사전점검 — 대상 명단 + 문제 인원 분류(출력 안 함) */
  @PostMapping("/card/print/bulk/check")
  @ResponseBody
  public ApiResponse<CardPrintService.BulkCheck> cardPrintBulkCheck(
      @RequestBody List<String> personIds, HttpSession session) {
    return ApiResponse.ok(cardPrintService.checkBulk(personIds, actor(session), menuId()));
  }

  /** 카드 일괄 출력 — 대상 전량 검증 후 앞/뒤 이미지 리스트 반환(실제 인쇄는 클라이언트 브라우저). */
  @PostMapping("/card/print/bulk")
  @ResponseBody
  public ApiResponse<List<String>> cardPrintBulk(
      @RequestBody List<String> personIds, HttpSession session) {
    return ApiResponse.ok(cardPrintService.printBulk(personIds, actor(session), menuId()));
  }

  /** 카드 프린트 요청 DTO. */
  public static class CardPrintReq {
    public String personId;
    public int cardId;
  }

  /** 증빙문서 다운로드 — 브라우저가 파일로 받도록 attachment 로 전송한다. */
  @GetMapping("/file")
  public ResponseEntity<ByteArrayResource> file(
      @RequestParam String personId, @RequestParam String fileType, HttpSession session) {
    TbPersonFile file = personFileService.download(personId, fileType, actor(session), menuId());
    String name = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new ByteArrayResource(file.getFileData()));
  }

  /** 선택 인원 일괄 삭제 (AJAX) */
  @DeleteMapping("/bulk")
  @ResponseBody
  public ApiResponse<Void> deleteMany(@RequestBody List<String> personIds, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning(
            personIds.size() + "건을 삭제했습니다.",
            personService.deleteMany(personIds, actor(session), menuId())));
  }

  /** 삭제 (AJAX) — 소프트 삭제 + BiostarX 사용자 삭제 */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam String personId, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("삭제되었습니다.", personService.delete(personId, actor(session), menuId())));
  }

  private static String withWarning(String message, String warn) {
    return warn == null ? message : message + " (BiostarX 사용자 동기화 실패: " + warn + ")";
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
