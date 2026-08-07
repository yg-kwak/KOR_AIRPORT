package AirPort.service;

import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정기 파기 — <b>1건</b> 처리. (배치 전체는 {@link VisitPurgeService})
 *
 * <p>별도 빈으로 둔 이유는 트랜잭션 때문이다. 같은 클래스 안에서 부르면 스프링 프록시를 거치지 않아 {@code @Transactional} 이 무시된다. 방문 한 건이
 * 통째로 커밋되거나 통째로 되돌아가야 하므로 경계를 여기서 잡는다.
 *
 * <p><b>되돌릴 수 없다.</b> 물리 삭제이며, 장비(BiostarX) 사용자도 함께 지운다.
 */
@Service
public class VisitPurgeItemService {

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final VisitBiostarService visitBiostar;

  public VisitPurgeItemService(
      TbVisitMapper visitMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      TbCardMapper cardMapper,
      VisitBiostarService visitBiostar) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.visitBiostar = visitBiostar;
  }

  /**
   * 방문 1건 파기.
   *
   * <p><b>장비를 먼저 지운다.</b> DB 를 먼저 지우면 어떤 BiostarX 사용자를 지워야 할지 알 수 없게 된다. 장비 삭제가 실패하면 예외로 되돌리고 다음
   * 회차에 다시 시도한다.
   *
   * @return 지운 방문객 수
   */
  @Transactional
  public int purgeVisit(int visitNo) {
    TbVisit visit = visitMapper.selectById(visitNo);
    if (visit == null) {
      return 0;
    }
    // 이 방문에서만 등장하는 방문객(정규인원 제외). 다른 방문에도 있으면 그쪽 이력이 깨지므로 남긴다.
    List<String> personIds = visitMapper.selectPurgeVisitorIds(visitNo);
    List<Integer> carIds = visitMapper.selectPurgeCarIds(visitNo);

    require(visitBiostar.deleteVisitors(visit.getVisitType(), personIds));
    for (String personId : personIds) {
      cardMapper.releaseByPerson(personId); // 카드는 자산이라 남기고 귀속만 푼다
      personMapper.purge(personId);
    }
    for (Integer carId : carIds) {
      cardMapper.releaseByCar(carId);
      carMapper.purge(carId); // 방문 차량만 — 기관차량은 목록에 들어오지 않는다
    }
    visitMapper.purgeVisitRows(visitNo);
    return personIds.size();
  }

  /**
   * 어느 방문에도 속하지 않은 채 남은 방문객 1명 파기.
   *
   * <p>방문 명단에서 빠지며 소프트 삭제된 사람들이다. 방문이 없어 위 경로로는 걸리지 않는데 개인정보는 그대로 남아 있다.
   */
  @Transactional
  public int purgeOrphan(String personId) {
    TbPerson person = personMapper.selectById(personId);
    if (person == null) {
      return 0;
    }
    // 장비 사용자도 남아 있을 수 있다. 부모 그룹은 그 사람의 발급유형으로 찾는다.
    require(visitBiostar.deleteVisitors(person.getPersonType(), List.of(personId)));
    cardMapper.releaseByPerson(personId);
    personMapper.purge(personId);
    return 1;
  }

  /** 장비 삭제 실패는 예외로 — DB 만 지우면 장비에 유령 사용자가 남는다. */
  private static void require(String fail) {
    if (fail != null) {
      throw new IllegalStateException("BiostarX 사용자 삭제 실패: " + fail);
    }
  }
}
