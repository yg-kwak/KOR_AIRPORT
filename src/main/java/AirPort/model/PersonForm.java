package AirPort.model;

import java.util.List;
import lombok.Data;

/**
 * 정규인원 등록 요청 — 인원정보 + 출입권한(tb_ac_group 선택) + 얼굴(사진/템플릿).
 *
 * <p>얼굴 3종은 파일 업로드(upload_picture) 또는 장치 촬영(credentials/face) 응답에서 받아 그대로 되돌려받는다. BiostarX 사용자
 * 생성 시 credentials.visualFaces 로 전송한다. (docs/integration.md)
 */
@Data
public class PersonForm {

  // ── 인원 정보 ──
  private String personId;
  private String personName;
  private String birthDate;
  private String personPhone;
  private String companyCode;
  private String titleCode;
  private String statusCode;
  private String mainTask;
  private String idCheckDt;
  private String idCheckFile; // 회보근거문서 파일명 (빈 값 = 문서 삭제)
  private String securityEduDt;
  private Integer securityEduScore;
  private String finalApproveDt;
  private String approveFile; // 승인근거문서 파일명 (빈 값 = 문서 삭제)
  private String accessStartDt;
  private String accessEndDt;
  private String remark;
  private String useYn;

  // ── 증빙문서 첨부(BASE64) — null/빈 값이면 기존 파일 유지, 파일명이 비면 삭제 (tb_person_file) ──
  private String idCheckFileData;
  private String approveFileData;

  // ── 사용자 권한: 선택한 tb_ac_group.ac_group_id 목록 ──
  private List<Integer> acGroupIds;

  // ── 카드정보: 화면 목록 그대로. 각 건은 추가 시점에 이미 BiostarX 에 등록돼 있다 ──
  private List<CardForm> cards;

  // ── 얼굴 ──
  private String faceImage; // template_ex_normalized_image (BASE64) — 사진/얼굴로 사용
  private String faceTemplate9; // credential_bin_type "9"
  private String faceTemplate5; // credential_bin_type "5"
}
