package AirPort.service;

import AirPort.adapter.BiostarFace;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import org.springframework.stereotype.Service;

/**
 * 인원 얼굴 — 사진 업로드·장치 촬영의 BiostarX 중계. (docs/integration.md)
 *
 * <p>브라우저가 BiostarX 를 직접 부를 수 없어 서버가 중계한다. 결과(정규화 이미지 + 템플릿 2종)는 화면이 들고 있다가 인원 저장 시 함께 전송된다.
 */
@Service
public class PersonFaceService {

  private final TbSystemMapper systemMapper;
  private final BiostarUserAdapter biostarUserAdapter;
  private final MenuAuthService menuAuthService;

  public PersonFaceService(
      TbSystemMapper systemMapper,
      BiostarUserAdapter biostarUserAdapter,
      MenuAuthService menuAuthService) {
    this.systemMapper = systemMapper;
    this.biostarUserAdapter = biostarUserAdapter;
    this.menuAuthService = menuAuthService;
  }

  /** 사진 파일 업로드 → 정규화 얼굴. */
  public BiostarFace uploadPicture(String base64Image, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (base64Image == null || base64Image.isBlank()) {
      return BiostarFace.fail("사진 데이터가 없습니다.");
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
    String devId = actor == null ? null : actor.getDevId();
    return biostarUserAdapter.captureFace(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), devId);
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }
}
