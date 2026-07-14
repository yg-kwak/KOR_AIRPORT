package AirPort.model;

import lombok.Data;

/** 비밀번호 변경 요청(헤더 계정 메뉴) — 이전/변경/변경확인. */
@Data
public class PasswordChangeForm {
  private String oldPassword;
  private String newPassword;
  private String confirmPassword;
}
