package AirPort.adapter.parking;

/**
 * 아마노 정기권 등록 1건 — 차량 하나분. (docs/integration.md)
 *
 * <p>주차장번호(lotAreaNo)는 설정값이라 여기 담지 않는다. {@code passType} 은 {@code passType1}~{@code passType8} 이며,
 * 어느 차량구역을 어느 종별로 보내는지는 공통코드 {@code CAR} 의 {@code code_tag} 가 정한다.
 *
 * @param carNo 차량번호(공백 없이 전체번호)
 * @param userName 표시 이름 — tb_car.car_name
 * @param passType 정기권 종별 {@code passType1}~{@code passType8}
 * @param startDate 시작일 yyyyMMdd
 * @param endDate 종료일 yyyyMMdd
 */
public record ParkingPassRequest(
    String carNo, String userName, String passType, String startDate, String endDate) {}
