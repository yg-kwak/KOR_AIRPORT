package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 사용자·얼굴 연동 어댑터 — 인원 등록 시 사용자 생성 및 얼굴(업로드/촬영). (docs/integration.md)
 *
 * <p>출입그룹/장치/사용자그룹 조회는 {@link BiostarAdapter} 담당. 세션은 {@link BiostarSession} 이 캐시·갱신한다.
 */
@Component
public class BiostarUserAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarUserAdapter.class);

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarUserAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /**
   * 사진 파일 업로드 → 정규화 얼굴 — {@code PUT /api/users/check/upload_picture}. 응답의 {@code image} 를 사진으로,
   * {@code image_template}/{@code image_template_2} 를 템플릿(9/5)으로 매핑한다.
   */
  public BiostarFace uploadPicture(String ip, String loginId, String password, String base64Image) {
    if (ip == null || ip.isBlank()) {
      return BiostarFace.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String body =
          objectMapper.writeValueAsString(Map.of("template_ex_picture", base64Image));
      HttpResponse<String> resp =
          session.put(baseUrl(ip), loginId, password, "/api/users/check/upload_picture", body);
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        return BiostarFace.fail(err);
      }
      JsonNode root = objectMapper.readTree(resp.body());
      String image = root.path("image").asText(null);
      if (image == null || image.isBlank()) {
        return BiostarFace.fail("응답에 사진(image)이 없습니다.");
      }
      return BiostarFace.ok(
          image,
          root.path("image_template").asText(null),
          root.path("image_template_2").asText(null));
    } catch (Exception e) {
      return BiostarFace.fail(friendlyError(e, "사진 업로드"));
    }
  }

  /**
   * 장치에서 얼굴 촬영 — {@code GET /api/devices/{devId}/credentials/face}. 응답 {@code
   * credentials.faces[0]} 의 정규화 이미지와 템플릿(credential_bin_type 9/5)을 뽑는다.
   */
  public BiostarFace captureFace(String ip, String loginId, String password, String devId) {
    if (ip == null || ip.isBlank()) {
      return BiostarFace.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (devId == null || devId.isBlank()) {
      return BiostarFace.fail("로그인 계정에 장치ID가 없습니다. 사용자관리에서 장치를 지정하세요.");
    }
    try {
      String path =
          "/api/devices/" + devId + "/credentials/face?pose_sensitivity=0&nonBlock=true";
      HttpResponse<String> resp = session.get(baseUrl(ip), loginId, password, path);
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        return BiostarFace.fail(err);
      }
      JsonNode faces = objectMapper.readTree(resp.body()).path("credentials").path("faces");
      if (!faces.isArray() || faces.isEmpty()) {
        return BiostarFace.fail("촬영된 얼굴이 없습니다. 장치에서 다시 시도하세요.");
      }
      JsonNode face = faces.get(0);
      String image = face.path("template_ex_normalized_image").asText(null);
      if (image == null || image.isBlank()) {
        return BiostarFace.fail("촬영 응답에 얼굴 이미지가 없습니다.");
      }
      return BiostarFace.ok(image, templateOf(face, "9"), templateOf(face, "5"));
    } catch (Exception e) {
      return BiostarFace.fail(friendlyError(e, "얼굴 촬영"));
    }
  }

  /** templates[] 에서 credential_bin_type 이 일치하는 template_ex 추출. */
  private String templateOf(JsonNode face, String binType) {
    JsonNode templates = face.path("templates");
    if (templates.isArray()) {
      for (JsonNode t : templates) {
        if (binType.equals(t.path("credential_bin_type").asText(""))) {
          return t.path("template_ex").asText(null);
        }
      }
    }
    return null;
  }

  /** BiostarX 사용자 생성 — {@code POST /api/users}. 실패 시 BiostarX 메시지를 그대로 돌려준다. */
  public BiostarResult createUser(
      String ip, String loginId, String password, BiostarUserRequest req) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      HttpResponse<String> resp =
          session.post(baseUrl(ip), loginId, password, "/api/users", userPayload(req));
      String err = BiostarAdapter.responseError(objectMapper, resp);
      return err == null ? BiostarResult.ok() : BiostarResult.fail(err);
    } catch (Exception e) {
      return BiostarResult.fail(friendlyError(e, "사용자 생성"));
    }
  }

  /** POST /api/users payload 구성 — null 필드는 넣지 않는다. */
  private String userPayload(BiostarUserRequest req) throws Exception {
    ObjectNode user = objectMapper.createObjectNode();
    putIfPresent(user, "name", req.name());
    putIfPresent(user, "photo", req.photo());
    putIfPresent(user, "phone", req.phone());
    putIfPresent(user, "user_id", req.userId());
    if (req.userGroupId() != null) {
      user.putObject("user_group_id").put("id", String.valueOf(req.userGroupId()));
    }
    putIfPresent(user, "disabled", req.disabled());
    putIfPresent(user, "start_datetime", req.startDatetime());
    putIfPresent(user, "expiry_datetime", req.expiryDatetime());
    putIfPresent(user, "user_title", req.userTitle());

    if (req.accessGroupIds() != null && !req.accessGroupIds().isEmpty()) {
      ArrayNode ags = user.putArray("access_groups");
      req.accessGroupIds().forEach(id -> ags.addObject().put("id", id));
    }

    if (req.faceImage() != null && !req.faceImage().isBlank()) {
      ObjectNode cred = user.putObject("credentials");
      ObjectNode face = cred.putArray("visualFaces").addObject();
      face.put("template_ex_normalized_image", req.faceImage());
      ArrayNode tpls = face.putArray("templates");
      addTemplate(tpls, req.faceTemplate9(), "9");
      addTemplate(tpls, req.faceTemplate5(), "5");
      face.put("flag", "1");
      face.put("useProfile", "true");
      cred.put("check_visualFace_img_validation", false);
    }

    ObjectNode root = objectMapper.createObjectNode();
    root.set("User", user);
    return objectMapper.writeValueAsString(root);
  }

  private static void addTemplate(ArrayNode templates, String templateEx, String binType) {
    if (templateEx != null && !templateEx.isBlank()) {
      templates.addObject().put("template_ex", templateEx).put("credential_bin_type", binType);
    }
  }

  private static void putIfPresent(ObjectNode node, String field, String value) {
    if (value != null && !value.isBlank()) {
      node.put(field, value);
    }
  }

  private String friendlyError(Exception e, String what) {
    if (e instanceof BiostarSessionException) {
      return e.getMessage();
    }
    if (e instanceof java.net.ConnectException) {
      return "BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.";
    }
    if (e instanceof java.net.http.HttpConnectTimeoutException) {
      return "연결 시간이 초과되었습니다.";
    }
    log.warn("BiostarX {} 오류: {}", what, e.toString());
    return e.getClass().getSimpleName();
  }

  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
