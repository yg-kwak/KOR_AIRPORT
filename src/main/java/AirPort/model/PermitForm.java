package AirPort.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 보호구역 임시출입허가 신청서 출력용 데이터. (임시인원등록 → [신청서 출력])
 *
 * <p>양식의 빈칸 중 <b>확인자·출입자의 근무확인·운전자·주소</b>는 시스템이 보관하지 않는다. 인쇄 후 손으로 적는 칸이라 비워 둔다.
 */
public class PermitForm {

  private String accessStart; // 출입시간 시작
  private String accessEnd; // 출입시간 종료
  private String carAreas; // 출입구역(차량) — 숫자만, 예 "1,2"
  private String personAreas; // 출입구역(인원) — 숫자만, 예 "2,3,4,5,6,7"
  private String purpose; // 출입목적
  private String applyDate; // 신청 일자(작업 시작일)
  private String applicantCompany; // 신청인 소속(첫 인솔자 기준)
  private String applicantName; // 신청인 성명

  private List<Visitor> visitors = new ArrayList<>();
  private List<Car> cars = new ArrayList<>();
  private List<Manager> managers = new ArrayList<>();

  /** 출입자 — 양식에서 한 행에 두 명씩 들어간다. */
  public static class Visitor {
    private String name;
    private String birthDate;
    private String cardNo; // 출입증번호(회수됐으면 마지막 카드번호)
    private String affiliation; // 출입자소속 및 주소 — 주소는 보관하지 않아 소속만

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getBirthDate() {
      return birthDate;
    }

    public void setBirthDate(String birthDate) {
      this.birthDate = birthDate;
    }

    public String getCardNo() {
      return cardNo;
    }

    public void setCardNo(String cardNo) {
      this.cardNo = cardNo;
    }

    public String getAffiliation() {
      return affiliation;
    }

    public void setAffiliation(String affiliation) {
      this.affiliation = affiliation;
    }
  }

  /** 방문 차량 — 한 행에 한 대. 운전자·주소는 양식에서 손으로 적는다. */
  public static class Car {
    private String carNo;
    private String carTypeName; // 차종
    private String cardNo; // 차량출입증번호

    public String getCarNo() {
      return carNo;
    }

    public void setCarNo(String carNo) {
      this.carNo = carNo;
    }

    public String getCarTypeName() {
      return carTypeName;
    }

    public void setCarTypeName(String carTypeName) {
      this.carTypeName = carTypeName;
    }

    public String getCardNo() {
      return cardNo;
    }

    public void setCardNo(String cardNo) {
      this.cardNo = cardNo;
    }
  }

  /** 인솔자 — 정규인원이라 소속·연락처가 있다. 양식에서 한 행에 두 명씩. */
  public static class Manager {
    private String company;
    private String name;
    private String cardNo;
    private String phone;

    public String getCompany() {
      return company;
    }

    public void setCompany(String company) {
      this.company = company;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getCardNo() {
      return cardNo;
    }

    public void setCardNo(String cardNo) {
      this.cardNo = cardNo;
    }

    public String getPhone() {
      return phone;
    }

    public void setPhone(String phone) {
      this.phone = phone;
    }
  }

  public String getAccessStart() {
    return accessStart;
  }

  public void setAccessStart(String accessStart) {
    this.accessStart = accessStart;
  }

  public String getAccessEnd() {
    return accessEnd;
  }

  public void setAccessEnd(String accessEnd) {
    this.accessEnd = accessEnd;
  }

  public String getCarAreas() {
    return carAreas;
  }

  public void setCarAreas(String carAreas) {
    this.carAreas = carAreas;
  }

  public String getPersonAreas() {
    return personAreas;
  }

  public void setPersonAreas(String personAreas) {
    this.personAreas = personAreas;
  }

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public String getApplyDate() {
    return applyDate;
  }

  public void setApplyDate(String applyDate) {
    this.applyDate = applyDate;
  }

  public String getApplicantCompany() {
    return applicantCompany;
  }

  public void setApplicantCompany(String applicantCompany) {
    this.applicantCompany = applicantCompany;
  }

  public String getApplicantName() {
    return applicantName;
  }

  public void setApplicantName(String applicantName) {
    this.applicantName = applicantName;
  }

  public List<Visitor> getVisitors() {
    return visitors;
  }

  public void setVisitors(List<Visitor> visitors) {
    this.visitors = visitors;
  }

  public List<Car> getCars() {
    return cars;
  }

  public void setCars(List<Car> cars) {
    this.cars = cars;
  }

  public List<Manager> getManagers() {
    return managers;
  }

  public void setManagers(List<Manager> managers) {
    this.managers = managers;
  }
}
