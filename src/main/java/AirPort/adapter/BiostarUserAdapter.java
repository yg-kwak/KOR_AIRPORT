package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
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
   * <b>{@code image_template}=bin_type 5, {@code image_template_2}=bin_type 9</b> 로 매핑한다(장치 촬영과 순서가
   * 반대). 템플릿은 고정 버퍼라 뒤가 널로 채워져 오므로 {@link #normalizeTemplate} 로 잘라 보낸다.
   */
  public BiostarFace uploadPicture(String ip, String loginId, String password, String base64Image) {
    if (ip == null || ip.isBlank()) {
      return BiostarFace.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String body = objectMapper.writeValueAsString(Map.of("template_ex_picture", base64Image));
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
      // 응답 매핑: image_template = bin_type 5, image_template_2 = bin_type 9 (장치 촬영과 반대 순서)
      return BiostarFace.ok(
          image,
          normalizeTemplate(root.path("image_template_2").asText(null)),
          normalizeTemplate(root.path("image_template").asText(null)));
    } catch (Exception e) {
      return BiostarFace.fail(friendlyError(e, "사진 업로드"));
    }
  }

  /**
   * 장치에서 얼굴 촬영 — {@code GET /api/devices/{devId}/credentials/face}. 응답 {@code credentials.faces[0]}
   * 의 정규화 이미지와 템플릿(credential_bin_type 9/5)을 뽑는다.
   */
  public BiostarFace captureFace(String ip, String loginId, String password, String devId) {
    if (ip == null || ip.isBlank()) {
      return BiostarFace.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (devId == null || devId.isBlank()) {
      return BiostarFace.fail("로그인 계정에 장치ID가 없습니다. 사용자관리에서 장치를 지정하세요.");
    }
    try {
      String path = "/api/devices/" + devId + "/credentials/face?pose_sensitivity=0&nonBlock=true";
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

  /**
   * BiostarX 사용자 존재 확인 — {@code GET /api/users/{userId}}. 있으면 true, 없으면 false.
   *
   * <p><b>통신 오류는 '없음'과 구분해 예외로 전파</b>한다(장비 장애 시 skip 로직이 성공으로 오판해 퇴실/삭제가 조용히 커밋되는 것을 막는다 — 정합성 판단용
   * 3상).
   */
  public boolean userExists(String ip, String loginId, String password, String userId) {
    if (ip == null || ip.isBlank() || userId == null || userId.isBlank()) {
      return false;
    }
    try {
      String path =
          "/api/users/"
              + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8);
      HttpResponse<String> resp = session.get(baseUrl(ip), loginId, password, path);
      return BiostarAdapter.responseError(objectMapper, resp) == null;
    } catch (BiostarSessionException e) {
      throw e; // 로그인/세션 오류 — 그대로 전파
    } catch (Exception e) {
      log.warn("BiostarX 사용자 확인 실패({}): {}", userId, e.toString());
      throw new BiostarSessionException(friendlyError(e, "사용자 확인"));
    }
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

  /**
   * BiostarX 사용자 수정 — {@code PUT /api/users/{userId}}. <b>변경된 항목만</b> 담아 보낸다.
   *
   * <p>있다가 없어진 값은 공란으로 보낸다(문자열 "", 목록 []). 얼굴을 지운 경우 {@code credentials.visualFaces=[]}. 변경이 없으면
   * 호출하지 않는다.
   */
  public BiostarResult updateUser(
      String ip,
      String loginId,
      String password,
      BiostarUserRequest before,
      BiostarUserRequest after) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      ObjectNode user = objectMapper.createObjectNode();
      putDelta(user, "name", before.name(), after.name());
      putDelta(user, "phone", before.phone(), after.phone());
      putDelta(user, "photo", before.photo(), after.photo());
      putDelta(user, "disabled", before.disabled(), after.disabled());
      putDelta(user, "start_datetime", before.startDatetime(), after.startDatetime());
      putDelta(user, "expiry_datetime", before.expiryDatetime(), after.expiryDatetime());
      putDelta(user, "user_title", before.userTitle(), after.userTitle());

      if (!java.util.Objects.equals(before.userGroupId(), after.userGroupId())) {
        user.putObject("user_group_id")
            .put("id", after.userGroupId() == null ? "" : String.valueOf(after.userGroupId()));
      }
      if (!sameIds(before.accessGroupIds(), after.accessGroupIds())) {
        ArrayNode ags = user.putArray("access_groups"); // 비면 [] (권한 전체 해제)
        if (after.accessGroupIds() != null) {
          after.accessGroupIds().forEach(id -> ags.addObject().put("id", id));
        }
      }
      if (!sameCards(before.cards(), after.cards())) {
        appendCards(user, after.cards()); // 비면 [] (카드 전체 회수)
        if (!user.has("cards")) {
          user.putArray("cards");
        }
      }
      applyFaceDelta(user, before, after);

      if (user.isEmpty()) {
        return BiostarResult.ok(); // 변경 없음 → 호출 생략
      }
      ObjectNode root = objectMapper.createObjectNode();
      root.set("User", user);
      HttpResponse<String> resp =
          session.put(
              baseUrl(ip),
              loginId,
              password,
              "/api/users/" + after.userId(),
              objectMapper.writeValueAsString(root));
      String err = BiostarAdapter.responseError(objectMapper, resp);
      return err == null ? BiostarResult.ok() : BiostarResult.fail(err);
    } catch (Exception e) {
      return BiostarResult.fail(friendlyError(e, "사용자 수정"));
    }
  }

  /** 얼굴 변경분 — 새로 등록/교체면 새 값, 있다가 지웠으면 빈 목록, 그대로면 미포함. */
  private void applyFaceDelta(
      ObjectNode user, BiostarUserRequest before, BiostarUserRequest after) {
    boolean had = notBlank(before.faceImage());
    boolean has = notBlank(after.faceImage());
    if (has && !java.util.Objects.equals(before.faceImage(), after.faceImage())) {
      ObjectNode cred = user.putObject("credentials");
      ObjectNode face = cred.putArray("visualFaces").addObject();
      face.put("template_ex_normalized_image", after.faceImage());
      ArrayNode tpls = face.putArray("templates");
      addTemplate(tpls, after.faceTemplate9(), "9");
      addTemplate(tpls, after.faceTemplate5(), "5");
      face.put("flag", "1");
      face.put("useProfile", "true");
      cred.put("check_visualFace_img_validation", false);
    } else if (had && !has) {
      ObjectNode cred = user.putObject("credentials");
      cred.putArray("visualFaces"); // 얼굴 삭제
      cred.put("check_visualFace_img_validation", false);
    }
  }

  /** BiostarX 사용자 삭제 — {@code DELETE /api/users?id={userId}&group_id={groupId}}. */
  public BiostarResult deleteUser(
      String ip, String loginId, String password, String userId, Integer groupId) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String path =
          "/api/users?id="
              + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8)
              + "&group_id="
              + (groupId == null ? "" : groupId);
      HttpResponse<String> resp = session.delete(baseUrl(ip), loginId, password, path);
      String err = BiostarAdapter.responseError(objectMapper, resp);
      return err == null ? BiostarResult.ok() : BiostarResult.fail(err);
    } catch (Exception e) {
      return BiostarResult.fail(friendlyError(e, "사용자 삭제"));
    }
  }

  /** 값이 바뀐 경우에만 담는다. 있다가 없어지면 공란(""). */
  private static void putDelta(ObjectNode node, String field, String before, String after) {
    if (!java.util.Objects.equals(nullToBlank(before), nullToBlank(after))) {
      node.put(field, nullToBlank(after));
    }
  }

  private static boolean sameIds(java.util.List<Integer> a, java.util.List<Integer> b) {
    java.util.Set<Integer> sa = a == null ? java.util.Set.of() : new java.util.HashSet<>(a);
    java.util.Set<Integer> sb = b == null ? java.util.Set.of() : new java.util.HashSet<>(b);
    return sa.equals(sb);
  }

  /** 카드 목록 — 비어 있지 않을 때만 cards[] 를 만든다(생성 payload 는 없는 필드를 넣지 않는다). */
  private static void appendCards(ObjectNode user, java.util.List<BiostarUserCard> cards) {
    if (cards == null || cards.isEmpty()) {
      return;
    }
    ArrayNode arr = user.putArray("cards");
    cards.forEach(c -> BiostarCardAdapter.appendCard(arr.addObject(), c));
  }

  private static boolean sameCards(
      java.util.List<BiostarUserCard> a, java.util.List<BiostarUserCard> b) {
    java.util.Set<String> sa = cardKeys(a);
    return sa.equals(cardKeys(b));
  }

  private static java.util.Set<String> cardKeys(java.util.List<BiostarUserCard> cards) {
    if (cards == null) {
      return java.util.Set.of();
    }
    java.util.Set<String> keys = new java.util.HashSet<>();
    cards.forEach(c -> keys.add(c.id() + "/" + c.cardNo()));
    return keys;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String nullToBlank(String s) {
    return s == null ? "" : s;
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

    appendCards(user, req.cards());

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

  /**
   * upload_picture 템플릿 정규화 — 응답 템플릿은 <b>고정 길이 버퍼</b>라 실제 데이터 뒤가 널(0x00)로 채워져 온다. 사용자 payload 의
   * {@code template_ex} 는 그 널 꼬리를 뺀 값이어야 하므로 잘라내고 표준 base64 로 다시 만든다(패딩 {@code ==} 포함). JSON 의
   * {@code \/} 이스케이프는 파싱 단계에서 이미 {@code /} 로 풀린다.
   */
  static String normalizeTemplate(String base64) {
    if (base64 == null || base64.isBlank()) {
      return null;
    }
    try {
      byte[] raw = Base64.getDecoder().decode(base64.trim());
      int end = raw.length;
      while (end > 0 && raw[end - 1] == 0) {
        end--; // 뒤쪽 널 패딩 제거(앞쪽 헤더의 0x00 은 그대로 유지)
      }
      return Base64.getEncoder().encodeToString(Arrays.copyOf(raw, end));
    } catch (IllegalArgumentException e) {
      return base64; // base64 가 아니면 원본 그대로(예상 밖 응답 방어)
    }
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
