package AirPort.config;

import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬(dev) 부팅 시 BiostarX 접속정보를 tb_system 에 시드한다 — 개발자가 실제 기기를 매번 설정관리 UI 로 넣지 않도록.
 *
 * <p><b>local 프로파일 전용.</b> 실제 접속정보는 git-ignore 된 {@code application-local.properties} 의 {@code
 * app.biostar.*} 에만 둔다(커밋 금지, security.md). {@code app.biostar.ip} 가 비면 아무것도 하지 않는다. 운영 프로파일에는 이
 * 시더가 로드되지 않으므로 tb_system 은 설정관리 화면으로만 관리된다.
 *
 * <p>매 로컬 부팅마다 properties 값으로 upsert → dev 의 단일 진실 원천은 이 로컬 파일이다.
 */
@Component
@Profile("local")
public class BiostarLocalSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BiostarLocalSeeder.class);

  private final TbSystemMapper systemMapper;
  private final String ip;
  private final String id;
  private final String pw;

  public BiostarLocalSeeder(
      TbSystemMapper systemMapper,
      @Value("${app.biostar.ip:}") String ip,
      @Value("${app.biostar.id:}") String id,
      @Value("${app.biostar.pw:}") String pw) {
    this.systemMapper = systemMapper;
    this.ip = ip;
    this.id = id;
    this.pw = pw;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (ip == null || ip.isBlank()) {
      return; // 로컬 접속정보 미설정 — 시드 생략
    }
    TbSystem row = new TbSystem();
    row.setBiostarIp(ip);
    row.setBiostarId(id);
    row.setBiostarPw(
        (pw == null || pw.isBlank()) ? null : ARIAUtil.ariaEncrypt(pw)); // 저장 규약: ARIA 암호화
    if (systemMapper.selectOne() == null) {
      systemMapper.insert(row);
    } else {
      systemMapper.update(row);
    }
    log.info("[local] BiostarX 접속정보 tb_system 시드 완료 (ip={})", ip); // 비밀번호는 로그 금지
  }
}
