package AirPort.service;

import AirPort.adapter.biostar.BiostarResult;
import AirPort.adapter.biostar.BiostarSessionException;
import AirPort.adapter.biostar.BiostarUserAdapter;
import AirPort.adapter.biostar.BiostarUserCard;
import AirPort.adapter.biostar.BiostarUserRequest;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbCommon;
import AirPort.model.TbCompany;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 정규인원 ↔ BiostarX 사용자 동기화 전담. (docs/integration.md — {@link VisitBiostarService} 와 같은 역할 분리 패턴)
 *
 * <p>존재 확인 후 upsert(있으면 PUT 델타, 없으면 POST 전체). 실패 사유 문자열을 돌려주며, <b>호출자(PersonService)는 null 이 아니면
 * 트랜잭션을 롤백</b>한다(장비-DB 정합성 최우선). 통신 오류는 '사용자 없음'과 구분해 실패로 취급한다.
 */
@Service
public class PersonBiostarService {

  private static final String PS = "PS";
  private static final String UT = "UT";

  private final TbSystemMapper systemMapper;
  private final TbCompanyMapper companyMapper;
  private final TbCommonMapper commonMapper;
  private final TbCardMapper cardMapper;
  private final TbPersonMapper personMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final BiostarUserAdapter biostarUserAdapter;

  public PersonBiostarService(
      TbSystemMapper systemMapper,
      TbCompanyMapper companyMapper,
      TbCommonMapper commonMapper,
      TbCardMapper cardMapper,
      TbPersonMapper personMapper,
      TbPersonAcGroupMapper acGroupMapper,
      BiostarUserAdapter biostarUserAdapter) {
    this.systemMapper = systemMapper;
    this.companyMapper = companyMapper;
    this.commonMapper = commonMapper;
    this.cardMapper = cardMapper;
    this.personMapper = personMapper;
    this.acGroupMapper = acGroupMapper;
    this.biostarUserAdapter = biostarUserAdapter;
  }

  /**
   * BiostarX 사용자 동기화(등록·수정 공통) — 실패 사유 문자열, 성공이면 null. 설정 없음/소속 기관 그룹 없음이면 장비 호출 전에 막는다. 반환이 null 이
   * 아니면 호출자가 트랜잭션을 롤백해야 한다(장비-DB 정합성 유지).
   */
  public String syncPersonToBiostar(PersonForm form, BiostarUserRequest before) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다. 설정관리에서 먼저 등록하세요.";
    }
    // 소속 기관에 BiostarX 사용자그룹이 없으면 사용자를 만들 수 없다 — 유령 인원 방지 위해 막는다
    if (companyGroupId(form.getCompanyCode()) == null) {
      return "소속 기관에 BiostarX 사용자그룹이 없습니다. 기관등록관리에서 해당 기관을 저장(동기화)해 그룹을 만든 뒤 다시 시도하세요.";
    }
    BiostarUserRequest after =
        biostarRequest(form, acGroupMapper.selectBiostarAcIds(form.getPersonId()));
    BiostarResult res;
    try {
      res = syncUser(cfg, before, after);
    } catch (BiostarSessionException e) {
      return e.getMessage(); // 통신·세션 오류 — '없음'으로 오판하지 않고 실패로 처리
    }
    if (!res.success()) {
      return res.message();
    }
    personMapper.updateBiostarUserId(form.getPersonId(), form.getPersonId());
    return null;
  }

  /** BiostarX 사용자 삭제 — 실패 사유 문자열, 성공이면 null. 호출자(PersonService.deleteOne)는 null 이 아니면 롤백한다. */
  public String deleteUser(String personId, String companyCode) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다. 설정관리에서 먼저 등록하세요.";
    }
    BiostarResult res =
        biostarUserAdapter.deleteUser(
            cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), personId, companyGroupId(companyCode));
    return res.success() ? null : res.message();
  }

  /**
   * BiostarX 사용자 동기화 — {@code GET /api/users/{인원ID}} 로 존재를 확인해 있으면 수정, 없으면 등록한다.
   *
   * <p>등록/수정 어느 쪽에서 들어와도 결과가 같아진다(우리 DB 와 BiostarX 가 어긋나 있어도 한 번에 맞춰진다).
   */
  private BiostarResult syncUser(
      TbSystem cfg, BiostarUserRequest before, BiostarUserRequest after) {
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    boolean exists = biostarUserAdapter.userExists(ip, id, pw(cfg), after.userId());
    return exists
        ? biostarUserAdapter.updateUser(ip, id, pw(cfg), before, after)
        : biostarUserAdapter.createUser(ip, id, pw(cfg), after);
  }

  /** 비교 기준이 없을 때 쓰는 빈 요청 — 모든 항목이 '변경됨'이 되어 전 항목이 전송된다. */
  public static BiostarUserRequest empty(String userId) {
    return new BiostarUserRequest(
        userId, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 저장된 인원(수정 전 상태) → BiostarX 전송 값. 얼굴 템플릿은 보관하지 않으므로 없음. */
  public BiostarUserRequest requestOf(TbPerson p, String faceImage, List<Integer> acIds) {
    return biostarRequest(
        p.getPersonId(),
        p.getPersonName(),
        p.getPersonPhone(),
        faceImage,
        p.getCompanyCode(),
        p.getStatusCode(),
        p.getAccessStartDt(),
        p.getAccessEndDt(),
        p.getTitleCode(),
        acIds,
        null,
        null,
        CardService.toBiostarCardsOf(cardMapper.selectByPerson(p.getPersonId())));
  }

  /**
   * 등록/수정 요청(폼) → BiostarX 전송 값.
   *
   * <p><b>비활성 상태면 얼굴을 함께 지운다.</b> 출입을 막아 놓고 생체정보만 장비에 남겨 두면, 상태를 되돌리는 순간 예전 얼굴로 문이 열린다. 퇴사·분실처럼 사람이
   * 떠났거나 카드를 잃은 상태에서는 남겨 둘 이유도 없다(개인정보 최소화).
   */
  private BiostarUserRequest biostarRequest(PersonForm f, List<Integer> acIds) {
    boolean disabled = isDisabled(f.getStatusCode());
    // 사용자 사진(photo)은 원본, 인증용 얼굴(visualFaces)은 장비가 돌려준 정규화 이미지를 쓴다.
    // 원본이 없으면(장치 촬영) 정규화 이미지를 사진으로도 쓴다.
    String photo =
        (f.getFacePhoto() != null && !f.getFacePhoto().isBlank())
            ? f.getFacePhoto()
            : f.getFaceImage();
    return biostarRequest(
        f.getPersonId(),
        f.getPersonName(),
        f.getPersonPhone(),
        disabled ? null : photo,
        f.getCompanyCode(),
        f.getStatusCode(),
        f.getAccessStartDt(),
        f.getAccessEndDt(),
        f.getTitleCode(),
        acIds,
        disabled ? null : f.getFaceTemplate9(),
        disabled ? null : f.getFaceTemplate5(),
        CardService.toBiostarCards(f.getCards()));
  }

  /** BiostarX 전송 값 구성(코드 → 실제 값 변환 포함). */
  private BiostarUserRequest biostarRequest(
      String personId,
      String name,
      String phone,
      String faceImage,
      String companyCode,
      String statusCode,
      String startDt,
      String endDt,
      String titleCode,
      List<Integer> acIds,
      String t9,
      String t5,
      List<BiostarUserCard> cards) {
    return new BiostarUserRequest(
        personId,
        name,
        phone,
        faceImage,
        companyGroupId(companyCode),
        codeTag(PS, statusCode),
        biostarDateTime(startDt, "00:00"),
        biostarDateTime(endDt, "23:59"),
        codeName(UT, titleCode),
        acIds,
        faceImage,
        t9,
        t5,
        cards,
        null); // 개인 인증 모드 미지정 — 정규인원은 얼굴+카드라 장비/사용자그룹 설정을 그대로 따른다
  }

  /** 기관의 BiostarX 사용자그룹 ID. */
  public Integer companyGroupId(String companyCode) {
    if (companyCode == null || companyCode.isBlank()) {
      return null;
    }
    TbCompany company = companyMapper.selectById(companyCode);
    return company == null ? null : company.getBiostarGroupId();
  }

  /** 공통코드의 code_name(예: UT 직위 → user_title). 없으면 null. PersonService 검증에서도 재사용. */
  public String codeName(String cmmId, String codeId) {
    TbCommon code = code(cmmId, codeId);
    return code == null ? null : code.getCodeName();
  }

  /**
   * 이 상태가 <b>비활성</b>인가 — {@code tb_common}(PS).code_tag 가 그대로 BiostarX 의 {@code disabled} 다.
   *
   * <p>정지·퇴사·회수·분실이 여기 해당한다. 어느 코드가 비활성인지는 <b>공통코드가 원천</b>이라 코드에 박지 않는다 — 현장에서 상태를 추가해도 이 판정이 따라간다.
   */
  public boolean isDisabled(String statusCode) {
    return "true".equalsIgnoreCase(codeTag(PS, statusCode));
  }

  /** 화면이 저장 전에 "얼굴이 지워진다"고 안내하려면 어떤 상태가 비활성인지 알아야 한다. */
  public List<String> disabledStatusCodes() {
    List<String> codes = new ArrayList<>();
    for (TbCommon c : commonMapper.selectByCodeTag(PS, "true")) {
      codes.add(c.getCodeId());
    }
    return codes;
  }

  /** 공통코드의 code_tag(예: PS 상태 → disabled 값). 없으면 null. */
  private String codeTag(String cmmId, String codeId) {
    TbCommon code = code(cmmId, codeId);
    return code == null ? null : code.getCodeTag();
  }

  private TbCommon code(String cmmId, String codeId) {
    return (codeId == null || codeId.isBlank()) ? null : commonMapper.selectOne(cmmId, codeId);
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  /**
   * 화면 값("YYYY-MM-DDTHH:mm" 또는 날짜만) → BiostarX 일시 형식(예: 2037-12-31T23:59:00.00Z).
   *
   * @param defaultTime 날짜만 들어온 경우 채울 시각("00:00"/"23:59")
   */
  private static String biostarDateTime(String value, String defaultTime) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    if (!v.contains("T")) {
      v = v + "T" + defaultTime; // 날짜만 오면 기본 시각 보정
    }
    if (v.length() == 16) {
      v = v + ":00"; // "YYYY-MM-DDTHH:mm" → 초 보정
    }
    return v + ".00Z";
  }
}
