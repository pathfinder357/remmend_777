package com.msa7.v1.delivery.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.msa7.v1.delivery.global.security.HeaderAuthenticationFilter;
import com.msa7.v1.delivery.global.security.InternalApiKeyFilter;

import lombok.RequiredArgsConstructor;

// @PreAuthorize를 실제로 동작시키려면 메서드 보안을 켜야 한다.
// 이 설정이 없으면 컨트롤러의 역할 검사가 조용히 무시된다.
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final HeaderAuthenticationFilter headerAuthenticationFilter;
	private final InternalApiKeyFilter internalApiKeyFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests((authorize) -> authorize
				// 내부 API는 사용자 헤더가 아니라 InternalApiKeyFilter가 부여한 서비스 권한을 요구한다.
				.requestMatchers("/api/v1/internal/**").hasRole("INTERNAL")
				.anyRequest().authenticated()
			);

		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)

			// 인증 정보를 서버 세션에 저장하지 않는다.
			// 매 요청마다 Gateway가 전달한 X-User-Id, X-User-Role을 기준으로 인증 정보를 생성한다.
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			);

		http
			.addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			// 내부 API 요청은 헤더 인증보다 먼저 API Key로 걸러낸다.
			.addFilterBefore(internalApiKeyFilter, HeaderAuthenticationFilter.class);
		return http.build();
	}
}
