package com.msa7.v1.order.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * 서비스 간 내부 API 호출을 인증한다.
 *
 * Gateway는 외부 요청의 X-User-Id, X-User-Role을 제거하고 JWT 기준으로 다시 채우지만,
 * 서비스 포트에 직접 접근하면 그 검증을 우회할 수 있다.
 * 내부 API는 사용자 헤더가 아니라 서비스만 아는 공유 키로 인증한다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

	public static final String API_KEY_HEADER = "X-Internal-Api-Key";
	private static final String INTERNAL_ROLE = "ROLE_INTERNAL";

	private final String expectedApiKey;

	public InternalApiKeyFilter(@Value("${internal.api-key}") String expectedApiKey) {
		this.expectedApiKey = expectedApiKey;
	}

	// 내부 API 요청에만 동작한다. 그 외 경로는 기존 헤더 인증 필터가 처리한다.
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return !uri.startsWith("/internal/") && !uri.startsWith("/api/v1/internal/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		if (!expectedApiKey.equals(request.getHeader(API_KEY_HEADER))) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		// 내부 호출에는 사용자 개념이 없으므로 서비스 전용 권한만 부여한다.
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(
				"internal-service",
				null,
				List.of(new SimpleGrantedAuthority(INTERNAL_ROLE))
			)
		);

		filterChain.doFilter(request, response);
	}
}
