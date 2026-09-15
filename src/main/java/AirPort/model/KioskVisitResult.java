package AirPort.model;

import lombok.Data;

/**
 * 키오스크 [등록 수정] 목록 한 줄 — 화면이 쓰는 값만.
 *
 * <p>무인증 응답이라 {@link TbVisit} 를 그대로 내보내지 않는다 — 인솔자 성명 암호문·소속처럼 화면이 쓰지 않는 값이 딸려 나간다.
 */
@Data
public class KioskVisitResult {
  private Integer visitNo;
  private String visitKind;
  private String workStartDt;
  private String workEndDt;
  private String workPurpose;
  private Integer personCount;
  private Integer carCount;

  public static KioskVisitResult of(TbVisit v) {
    KioskVisitResult r = new KioskVisitResult();
    r.visitNo = v.getVisitNo();
    r.visitKind = v.getVisitKind();
    r.workStartDt = v.getWorkStartDt();
    r.workEndDt = v.getWorkEndDt();
    r.workPurpose = v.getWorkPurpose();
    r.personCount = v.getPersonCount();
    r.carCount = v.getCarCount();
    return r;
  }
}
