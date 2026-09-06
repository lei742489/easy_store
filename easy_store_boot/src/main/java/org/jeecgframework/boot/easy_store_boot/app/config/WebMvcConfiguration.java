package org.jeecgframework.boot.easy_store_boot.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.*;
import org.jeecgframework.boot.easy_store_boot.app.config.handler.ClientVersionInterceptor;

/**
 * Spring Boot 2.0 解决跨域问题
 *
 * @Author qinfeng
 *
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

	@Value("${es-app.path.upload}")
	private String upLoadPath;
	@Value("${es-app.path.webapp}")
	private String webAppPath;
	@Value("${spring.resource.static-locations}")
	private String staticLocations;

	@Bean
	public ClientVersionInterceptor clientVersionInterceptor() {
		return new ClientVersionInterceptor();
	}

	@Bean
	public CorsFilter corsFilter() {
		final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		final CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.addAllowedOriginPattern("*"); // 替代 addAllowedOrigin("*")
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("file:" + upLoadPath + "//", "file:" + webAppPath + "//")
				.addResourceLocations(staticLocations.split(","));
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("index.html");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(clientVersionInterceptor())
				.addPathPatterns("/api/**")
				.excludePathPatterns(
						"/api/upload/static/**",
						"/api/upload/pdf/**",
						"/api/health",
						"/api/**/exportXls"
				);
	}

}
