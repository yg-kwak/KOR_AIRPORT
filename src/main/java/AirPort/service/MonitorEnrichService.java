package AirPort.service;

import AirPort.adapter.biostar.BiostarAuthEvent;
import AirPort.adapter.biostar.BiostarEventAdapter;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.AuthEventResult;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.model.TbVisit;
import AirPort.security.ARIAUtil;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 장비 인증 이벤트 한 건에 <b>우리 DB 값</b>을 붙인다 (모니터링 → 실시간 이벤트).
 *
 * <p>{@link MonitorService} 에서 떼어낸 조각이다. 그쪽은 소켓 수명·구독자 관리·감사를 들고 있고, 여기는 <b>한 건을 화면에 올릴 모양으로 만드는
 * 일</b>만 한다. 둘을 한 클래스에 두면 성격이 다른 변경이 같은 파일에 쌓인다.
 *
 * <p>없는 값은 비워 둔다 — 미등록 인증도 화면에는 보여야 한다(누가 지나갔는지가 정보다).
 */
@Service
public class MonitorEnrichService {

  private static final Logger log = LoggerFactory.getLogger(MonitorEnrichService.class);

  /** 정규인원 — 허가구역·허가기간을 사람에게 직접 붙인다. 그 밖은 방문 단위로 잡힌다. */
  private static final String PERSON_TYPE_REGULAR = "PT01";

  /** 구역명에서 번호만 — "인원구역3" → 3. (신청서 출력과 같은 규칙) */
  private static final Pattern AREA_NO = Pattern.compile("(\\d+)");

  private final TbSystemMapper systemMapper;
  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final TbVisitMapper visitMapper;
  private final BiostarEventAdapter eventAdapter;

  public MonitorEnrichService(
      TbSystemMapper systemMapper,
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      TbVisitMapper visitMapper,
      BiostarEventAdapter eventAdapter) {
    this.systemMapper = systemMapper;
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.visitMapper = visitMapper;
    this.eventAdapter = eventAdapter;
  }

  /**
   * 장비 이벤트 + 우리 DB 값.
   *
   * <p>인원 조회는 <b>화면 전용 단건 조회 한 번</b>이다({@code selectForMonitor}). 인증 1건마다 도는 자리라 왕복이 그대로 곱해진다 —
   * 성명·소속·기관명·출입기간을 한 번에 받는다.
   */
  public AuthEventResult enrich(BiostarAuthEvent event) {
    AuthEventResult row = new AuthEventResult();
    row.setEventTime(time(event.datetime()));
    row.setDeviceId(event.deviceId());
    row.setDeviceName(event.deviceName());
    row.setPersonId(event.userId());
    row.setResultLabel(event.resultLabel()); // 표기 문구는 서버가 정한다
    row.setGranted(event.granted()); // 통과/거부 — 화면 색이 갈린다

    TbPerson person =
        (event.userId() == null) ? null : personMapper.selectForMonitor(event.userId());
    if (person != null) {
      row.setPersonName(decrypt(person.getPersonName()));
      row.setCompanyName(affiliationOf(person));
      row.setAreas(areaNos(acGroupNames(person)));
      row.setPeriod(period(person));
      row.setRegisteredPhoto(photoMapper.selectPhoto(event.userId()));
      // 사진 없는 칸에 사람 모양을 세울지 카드 모양을 세울지 — 정규인원만 얼굴이 있어야 정상이다
      row.setFaceUser(PERSON_TYPE_REGULAR.equals(person.getPersonType()));
    }

    TbSystem cfg = systemMapper.selectOne();
    if (cfg != null && event.imageId() != null) {
      row.setAuthPhoto(
          eventAdapter.authImage(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), event.imageId()));
    }
    // 사진이 왜 안 나오는지는 이 한 줄로 판가름난다 — 이벤트에 사진 ID 가 없었는지, 장비가 안 줬는지,
    // 우리 DB 에 등록사진이 없었는지. 평소엔 필요 없어 DEBUG 다(상시 운용에서 인증마다 쌓인다).
    log.debug(
        "인증 {}({}) 인원={} 사진ID={} 인증사진={} 등록사진={}",
        event.eventName(),
        event.eventCode(),
        event.userId(),
        event.imageId() == null ? "없음" : event.imageId(),
        row.getAuthPhoto() == null ? "없음" : row.getAuthPhoto().length() + "자",
        row.getRegisteredPhoto() == null ? "없음" : row.getRegisteredPhoto().length() + "자");
    return row;
  }

  /**
   * 소속 — 방문객은 자유입력한 {@code affiliation}, 정규인원은 기관명.
   *
   * <p>방문객은 기관(`tb_company`)에 매이지 않고 소속을 직접 적는다. 그 값이 있으면 그것이 정확하다. 비어 있을 때만 기관명으로 물러선다. 기관명은 위 조회가
   * 조인으로 함께 받아 온다 — 따로 묻지 않는다.
   */
  private String affiliationOf(TbPerson person) {
    String affiliation = person.getAffiliation();
    return (affiliation != null && !affiliation.isBlank()) ? affiliation : person.getCompanyName();
  }

  /**
   * 허가구역의 출처는 인원 구분에 따라 다르다.
   *
   * <ul>
   *   <li>정규인원 — 사람에게 직접 붙은 출입그룹(`tb_person_ac_group`)
   *   <li>그 밖(임시·장기·상주·순찰·대여) — <b>방문 단위</b>로 잡힌 구역(`tb_visit_ac_group`). 여기를 안 보면 방문객은 허가구역이 늘 비어
   *       보인다.
   * </ul>
   */
  private List<String> acGroupNames(TbPerson person) {
    return PERSON_TYPE_REGULAR.equals(person.getPersonType())
        ? acGroupMapper.selectAcGroupNames(person.getPersonId())
        : visitMapper.selectAcGroupNamesByPerson(person.getPersonId());
  }

  /**
   * 허가기간 — 출처가 허가구역과 같은 갈래로 나뉜다.
   *
   * <ul>
   *   <li>정규인원 — 사람에게 붙은 출입기간(`tb_person.access_start_dt`~`access_end_dt`)
   *   <li>그 밖 — <b>방문 단위</b>의 작업기간(`tb_visit.work_start_dt`~`work_end_dt`). 구역과 <b>같은 방문</b>을 본다 —
   *       방문 선택 기준은 mapper 의 `latestVisitOfPerson` 조각 하나가 원천이다.
   * </ul>
   *
   * <p>둘 다 없으면 {@code null} 이다 — 화면은 "-" 로 둔다. 미등록 인증도 화면에는 올라와야 하므로 없는 값에 예외를 던지지 않는다.
   */
  private String period(TbPerson person) {
    if (PERSON_TYPE_REGULAR.equals(person.getPersonType())) {
      return range(person.getAccessStartDt(), person.getAccessEndDt());
    }
    TbVisit visit = visitMapper.selectLatestVisitByPerson(person.getPersonId());
    return visit == null ? null : range(visit.getWorkStartDt(), visit.getWorkEndDt());
  }

  /**
   * 기간 표기 — "2026-01-01 09:00:00 ~ 2037-12-31 23:59:00".
   *
   * <p>정규·방문 구분 없이 <b>같은 형식</b>이다. 화면에서 두 인원의 값을 나란히 볼 때 형식이 다르면 같은 뜻인지 매번 되짚어야 한다.
   *
   * <p>초까지 쓴다. 출입 판정은 초 단위로 일어나므로, 경계에 걸린 사람을 두고 "왜 막혔나"를 볼 때 분까지만으로는 답이 나오지 않는다. 값은 DB 에서 초까지 읽어
   * 온다 — 없는 초를 {@code :00} 으로 지어내지 않는다.
   *
   * <p>한쪽만 비어 있어도 있는 쪽은 보여 준다(빈 쪽은 "?"). 기간이 반쯤 비었다는 사실 자체가 현장에서 확인할 거리다.
   */
  private static String range(String start, String end) {
    String s = stamp(start);
    String e = stamp(end);
    if (s == null && e == null) {
      return null;
    }
    return (s == null ? "?" : s) + " ~ " + (e == null ? "?" : e);
  }

  /** SQL 이 "YYYY-MM-DDThh:mm:ss"(style 126)로 내려준다. 화면에는 가운데 T 대신 공백을 쓴다. */
  private static String stamp(String dt) {
    return (dt == null || dt.isBlank()) ? null : dt.replace('T', ' ');
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }

  /** "2026-08-11T01:38:01.00Z" → "01:38:01". 형식이 다르면 원문을 남긴다(조용히 비우지 않는다). */
  static String time(String datetime) {
    if (datetime == null || datetime.length() < 19 || datetime.charAt(10) != 'T') {
      return datetime;
    }
    return datetime.substring(11, 19);
  }

  /**
   * 출입그룹 이름 목록 → 구역 번호를 이어 붙인다. 예: [인원구역1, 인원구역2, 인원구역5] → "125"
   *
   * <p>번호가 없는 이름은 그대로 남긴다 — 조용히 사라지면 어느 구역이 빠졌는지 알 수 없다.
   */
  static String areaNos(List<String> names) {
    StringBuilder sb = new StringBuilder();
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Matcher m = AREA_NO.matcher(name);
      sb.append(m.find() ? m.group(1) : name.trim());
    }
    return sb.toString();
  }
}
