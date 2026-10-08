package AirPort.service;

import AirPort.adapter.biostar.BiostarDoor;
import AirPort.adapter.biostar.BiostarDoorAdapter;
import AirPort.adapter.biostar.BiostarResult;
import AirPort.adapter.biostar.BiostarSessionException;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbGraphicMapMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.GraphicMapForm;
import AirPort.model.TbGraphicMap;
import AirPort.model.TbGraphicMapDoor;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그래픽맵 — 평면도를 올리고 그 위에 <b>BiostarX 출입문</b>을 놓는다. 놓인 문은 원격으로 개방·잠금·해제할 수 있다. 인증 이벤트는 {@link
 * MonitorService} 가 그대로 흘려 준다(이 화면은 놓인 문의 입구 단말기들을 구독할 뿐이다).
 *
 * <p>평면도는 <b>래스터 이미지만</b> 받는다(PNG·JPG·GIF·WEBP). SVG 는 스크립트를 품을 수 있어 받지 않는다. 형식은 업로드한 쪽이 붙인 MIME 이
 * 아니라 <b>파일 앞부분의 서명</b>으로 판정한다 — 확장자만 바꾼 파일이 이미지로 저장되지 않게.
 */
@Service
public class GraphicMapService {

  /** 평면도 한 장 상한 — 업로드 한도(spring.servlet.multipart.max-file-size)와 같게 둔다. */
  static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

  private final TbGraphicMapMapper mapMapper;
  private final TbSystemMapper systemMapper;
  private final BiostarDoorAdapter doorAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public GraphicMapService(
      TbGraphicMapMapper mapMapper,
      TbSystemMapper systemMapper,
      BiostarDoorAdapter doorAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.mapMapper = mapMapper;
    this.systemMapper = systemMapper;
    this.doorAdapter = doorAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 놓을 수 있는 출입문 — BiostarX 에서 그대로 읽는다(전체 출입문 그룹). 배치는 편집 권한이 있어야 하므로 목록도 그 권한으로 연다. */
  public List<BiostarDoor> biostarDoors(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem cfg = config();
    try {
      return doorAdapter.searchDoors(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg));
    } catch (BiostarSessionException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
    }
  }

  /**
   * 출입문 원격 제어 — 개방·잠금·해제. <b>이 맵에 놓인 문만</b> 받는다(요청의 문 번호만 바꿔 평면도에 없는 문을 열지 못하게).
   *
   * <p>문을 여는 일이라 성공·실패 모두 감사에 남긴다 — 실패는 롤백과 무관하게 남아야 해서 {@code logAlways}.
   */
  public String controlDoor(
      int mapId, long doorId, String action, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정 권한 — 문을 움직이는 일은 보기 권한으로 하지 않는다
    BiostarDoorAdapter.Action act = action(action);
    TbGraphicMapDoor door = mapMapper.selectPlacedDoor(mapId, doorId);
    if (door == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "이 맵에 놓인 출입문이 아닙니다.");
    }
    TbSystem cfg = config();
    BiostarResult res =
        doorAdapter.control(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), act, doorId);
    String what =
        "출입문 " + act.label + ": " + doorId + " " + nvl(door.getDoorName()) + " (맵 " + mapId + ")";
    if (!res.success()) {
      auditService.logAlways(actor, AuditService.UPDATE, menuId, what + " 실패 — " + res.message());
      throw new BusinessException(ErrorCode.INVALID_INPUT, act.label + " 실패: " + res.message());
    }
    auditService.log(actor, AuditService.UPDATE, menuId, what);
    return nvl(door.getDoorName()) + " " + act.label + " 요청을 보냈습니다.";
  }

  static BiostarDoorAdapter.Action action(String action) {
    if (action != null) {
      for (BiostarDoorAdapter.Action a : BiostarDoorAdapter.Action.values()) {
        if (a.name().equalsIgnoreCase(action.trim())) {
          return a;
        }
      }
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 제어입니다: " + action);
  }

  private TbSystem config() {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null || cfg.getBiostarIp() == null || cfg.getBiostarIp().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 접속정보가 없습니다. 설정관리에서 등록하세요.");
    }
    return cfg;
  }

  private static String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  public List<TbGraphicMap> maps(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return mapMapper.selectList();
  }

  /** 평면도 이미지 — 없으면 404. */
  public TbGraphicMap image(int mapId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbGraphicMap m = mapMapper.selectImage(mapId);
    if (m == null || m.getImageData() == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "평면도 이미지가 없습니다.");
    }
    return m;
  }

  public List<TbGraphicMapDoor> doors(int mapId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    requireMap(mapId);
    return mapMapper.selectDoors(mapId);
  }

  /** 평면도 추가 — 이름 + 이미지. */
  @Transactional
  public int create(String mapName, byte[] image, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbGraphicMap row = new TbGraphicMap();
    row.setMapName(requireName(mapName));
    row.setImageData(image);
    row.setImageType(imageType(image));
    mapMapper.insert(row);
    auditService.log(
        actor, AuditService.CREATE, menuId, "그래픽맵 추가: " + row.getMapId() + " " + row.getMapName());
    return row.getMapId();
  }

  /** 평면도 이미지 교체 — 출입문 위치는 비율이라 그대로 남는다(같은 도면의 새 판이면 대개 맞다). */
  @Transactional
  public void replaceImage(int mapId, byte[] image, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    requireMap(mapId);
    mapMapper.updateImage(mapId, image, imageType(image));
    auditService.log(actor, AuditService.UPDATE, menuId, "그래픽맵 평면도 교체: " + mapId);
  }

  /** 이름 변경, 그리고 doors 가 있으면 출입문 배치를 통째로 바꾼다(편집을 마치고 한 번에 저장). */
  @Transactional
  public void update(GraphicMapForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    if (form.getMapId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "맵번호가 필요합니다.");
    }
    int mapId = form.getMapId();
    requireMap(mapId);
    if (form.getMapName() != null) {
      mapMapper.updateName(mapId, requireName(form.getMapName()));
    }
    if (form.getDoors() != null) {
      List<TbGraphicMapDoor> doors = cleanDoors(form.getDoors());
      mapMapper.deleteDoors(mapId);
      if (!doors.isEmpty()) {
        mapMapper.insertDoors(mapId, doors);
      }
      auditService.log(
          actor, AuditService.UPDATE, menuId, "그래픽맵 출입문 배치: " + mapId + " (" + doors.size() + "개)");
    } else {
      auditService.log(actor, AuditService.UPDATE, menuId, "그래픽맵 이름 변경: " + mapId);
    }
  }

  @Transactional
  public void delete(int mapId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    requireMap(mapId);
    mapMapper.deleteDoors(mapId); // 배치는 맵에 딸린 설정이라 함께 정리한다
    mapMapper.softDelete(mapId);
    auditService.log(actor, AuditService.DELETE, menuId, "그래픽맵 삭제: " + mapId);
  }

  /**
   * 배치 정리 — 같은 출입문은 한 맵에 한 번만, 위치는 평면도 안(0~1)으로 자르고 소수 넷째 자리까지.
   *
   * <p>화면이 끌어다 놓다가 경계를 살짝 넘긴 값이 그대로 저장되면 다시 열었을 때 평면도 밖에 서서 찾을 수 없다.
   */
  static List<TbGraphicMapDoor> cleanDoors(List<TbGraphicMapDoor> in) {
    List<TbGraphicMapDoor> out = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (TbGraphicMapDoor d : in) {
      if (d == null || d.getDoorId() == null) {
        continue;
      }
      if (!seen.add(d.getDoorId())) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "같은 출입문을 한 맵에 두 번 놓을 수 없습니다: " + d.getDoorId());
      }
      TbGraphicMapDoor c = new TbGraphicMapDoor();
      c.setDoorId(d.getDoorId());
      c.setDoorName(d.getDoorName() == null ? null : d.getDoorName().trim());
      c.setDeviceId(
          d.getDeviceId() == null || d.getDeviceId().isBlank() ? null : d.getDeviceId().trim());
      c.setPosX(clamp(d.getPosX()));
      c.setPosY(clamp(d.getPosY()));
      out.add(c);
    }
    return out;
  }

  private static BigDecimal clamp(BigDecimal v) {
    if (v == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "출입문 위치가 없습니다.");
    }
    BigDecimal c = v.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    return c.setScale(4, RoundingMode.HALF_UP);
  }

  /**
   * 이미지 형식 — 파일 앞부분의 서명으로 판정한다. 래스터 이미지가 아니면(또는 비었거나 너무 크면) 거절.
   *
   * <p>업로드한 쪽이 붙인 MIME·확장자는 믿지 않는다. 그 값으로 응답의 Content-Type 을 정하면 HTML 을 이미지라 우겨 올린 파일이 이 화면의 출처로 열릴
   * 수 있다.
   */
  static String imageType(byte[] b) {
    if (b == null || b.length == 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "평면도 이미지를 선택하세요.");
    }
    if (b.length > MAX_IMAGE_BYTES) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "평면도 이미지는 5MB 까지 올릴 수 있습니다.");
    }
    if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
      return "image/png";
    }
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
      return "image/gif";
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return "image/webp";
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT, "평면도는 PNG·JPG·GIF·WEBP 이미지만 올릴 수 있습니다.");
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "맵 이름을 입력하세요.");
    }
    String n = name.trim();
    if (n.length() > 100) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "맵 이름은 100자까지입니다.");
    }
    return n;
  }

  private void requireMap(int mapId) {
    TbGraphicMap m = mapMapper.selectById(mapId);
    if (m == null || "Y".equals(m.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "맵을 찾을 수 없습니다.");
    }
  }
}
