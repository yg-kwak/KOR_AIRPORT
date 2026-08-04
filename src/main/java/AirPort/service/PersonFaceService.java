package AirPort.service;

import AirPort.adapter.BiostarFace;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * 인원 얼굴 — 사진 업로드·장치 촬영의 BiostarX 중계. (docs/integration.md)
 *
 * <p>브라우저가 BiostarX 를 직접 부를 수 없어 서버가 중계한다. 결과(정규화 이미지 + 템플릿 2종)는 화면이 들고 있다가 인원 저장 시 함께 전송된다.
 */
@Service
public class PersonFaceService {

  private final TbSystemMapper systemMapper;
  private final TbLoginUserMapper loginUserMapper;
  private final BiostarUserAdapter biostarUserAdapter;
  private final MenuAuthService menuAuthService;

  public PersonFaceService(
      TbSystemMapper systemMapper,
      TbLoginUserMapper loginUserMapper,
      BiostarUserAdapter biostarUserAdapter,
      MenuAuthService menuAuthService) {
    this.systemMapper = systemMapper;
    this.loginUserMapper = loginUserMapper;
    this.biostarUserAdapter = biostarUserAdapter;
    this.menuAuthService = menuAuthService;
  }

  /** 업로드 사진 최대 크기 — 요청 크기 제한(5MB, application.properties) 안쪽으로 잡는다. */
  private static final int MAX_PHOTO_BYTES = 4 * 1024 * 1024;

  /** 사진 파일 업로드 → 정규화 얼굴. */
  public BiostarFace uploadPicture(String base64Image, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (base64Image == null || base64Image.isBlank()) {
      return BiostarFace.fail("사진 데이터가 없습니다.");
    }
    // BiostarX 로 보내기 전에 걸러낼 수 있는 사유(형식·용량)는 여기서 구체적으로 알린다 — 장비까지 가면 HTTP 400 만 돌아온다
    String reject = rejectReason(base64Image);
    if (reject != null) {
      return BiostarFace.fail(reject);
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarFace.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    return biostarUserAdapter.uploadPicture(
        cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), base64Image);
  }

  /** 로그인 계정의 장치(tb_login_user.dev_id)로 얼굴 촬영. */
  public BiostarFace captureFace(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarFace.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    // 장치는 DB 에서 다시 읽는다 — 세션 값은 로그인 시점 스냅샷이라 변경이 즉시 반영되지 않는다
    String devId = CardService.currentDevId(loginUserMapper, actor);
    return biostarUserAdapter.captureFace(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), devId);
  }

  /**
   * 사진 사전 검증 — 통과하면 null, 아니면 사용자에게 보일 사유. base64 자체가 깨졌거나 JPG/PNG 가 아니거나 너무 큰 경우를 잡는다.
   *
   * <p>확장자·MIME 이 아니라 <b>선두 바이트</b>로 판정한다(이름만 .jpg 인 HEIC 등을 걸러야 한다). 테스트가 직접 부를 수 있게 패키지 프라이빗으로
   * 둔다.
   */
  static String rejectReason(String base64Image) {
    byte[] raw;
    try {
      // MIME 디코더 — 줄바꿈이 섞인 base64 도 받는다(strict 디코더는 '데이터가 올바르지 않습니다'로 잘못 안내)
      raw = Base64.getMimeDecoder().decode(base64Image.trim());
    } catch (IllegalArgumentException e) {
      return "사진 데이터가 올바르지 않습니다. 파일을 다시 선택하세요.";
    }
    if (raw.length > MAX_PHOTO_BYTES) {
      return String.format(
          "사진이 너무 큽니다(%.1fMB). %dMB 이하로 줄여서 다시 선택하세요.",
          raw.length / 1024.0 / 1024.0, MAX_PHOTO_BYTES / 1024 / 1024);
    }
    if (!isJpeg(raw) && !isPng(raw)) {
      return "JPG 또는 PNG 사진만 등록할 수 있습니다. (HEIC·BMP 등은 JPG 로 변환해 주세요)";
    }
    return null;
  }

  /** JPEG 선두 바이트 FF D8 FF. */
  private static boolean isJpeg(byte[] b) {
    return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
  }

  /** PNG 선두 바이트 89 50 4E 47. */
  private static boolean isPng(byte[] b) {
    return b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }
}
