package AirPort.service;

import AirPort.mapper.TbLoginUserMapper;
import AirPort.model.TbLoginUser;
import AirPort.security.ARIAUtil;
import org.springframework.stereotype.Service;

/** 로그인 인증. 비밀번호는 ARIA 암호문(hex)으로 비교한다. (docs/security.md) */
@Service
public class LoginService {

  private final TbLoginUserMapper loginUserMapper;

  public LoginService(TbLoginUserMapper loginUserMapper) {
    this.loginUserMapper = loginUserMapper;
  }

  /**
   * @return 인증 성공 시 세션용 사용자(비밀번호 제거), 실패 시 null
   */
  public TbLoginUser authenticate(String userId, String rawPassword) {
    TbLoginUser user = loginUserMapper.selectById(userId);
    if (user == null || !"Y".equals(user.getUseYn())) {
      return null;
    }
    String encrypted = ARIAUtil.ariaEncrypt(rawPassword);
    if (!encrypted.equals(user.getPassword())) {
      return null;
    }
    user.setPassword(null); // 세션에 비밀번호를 담지 않는다
    user.setUserName(ARIAUtil.ariaDecrypt(user.getUserName())); // 성명 복호화(표시용)
    return user;
  }
}
