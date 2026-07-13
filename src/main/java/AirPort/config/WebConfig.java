package AirPort.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 설정 — AuthInterceptor 등록. ⚠️ 인터셉터가 실제 등록되어야 보안 경계가 성립한다(등록 누락 = 보안 회귀). (docs/security.md) */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String[] EXCLUDES = {
    "/login",
    "/logout",
    "/error",
    "/favicon.ico",
    "/css/**",
    "/js/**",
    "/ic/**",
    "/images/**",
    "/font/**"
  };

  private final AuthInterceptor authInterceptor;
  private final MenuAccessInterceptor menuAccessInterceptor;

  public WebConfig(AuthInterceptor authInterceptor, MenuAccessInterceptor menuAccessInterceptor) {
    this.authInterceptor = authInterceptor;
    this.menuAccessInterceptor = menuAccessInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 인증 먼저, 그 다음 메뉴 해석/접속감사(인증된 요청만 대상)
    registry.addInterceptor(authInterceptor).addPathPatterns("/**").excludePathPatterns(EXCLUDES);
    registry
        .addInterceptor(menuAccessInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(EXCLUDES);
  }
}
