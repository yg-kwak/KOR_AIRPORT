package AirPort.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 설정 — AuthInterceptor 등록. ⚠️ 인터셉터가 실제 등록되어야 보안 경계가 성립한다(등록 누락 = 보안 회귀). (docs/security.md) */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;

  public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(authInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(
            "/login",
            "/logout",
            "/error",
            "/favicon.ico",
            "/css/**",
            "/js/**",
            "/ic/**",
            "/images/**",
            "/font/**");
  }
}
