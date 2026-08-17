package AirPort.mapper;

import AirPort.model.ParkingEventSearchParam;
import AirPort.model.TbParkingEvent;
import java.util.List;

/** 주차 입·출차 이벤트 매퍼. SQL 은 mapper/TbParkingEventMapper.xml. */
public interface TbParkingEventMapper {

  /**
   * 이벤트 저장. 같은 (event_type, car_no, event_dt) 가 이미 있으면 아무것도 넣지 않고 0 을 돌려준다 — 주차서버는 응답을 못 받으면 같은 건을
   * 다시 보낸다.
   */
  int insert(TbParkingEvent row);

  // ── 주차 조회 화면(조회 전용) ─────────────────────────────
  List<TbParkingEvent> selectList(ParkingEventSearchParam param);

  long selectCount(ParkingEventSearchParam param);
}
