package com.msa7.hub.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import feign.RequestInterceptor;
import feign.RequestTemplate;

// 모든 Feign 호출에 내부 API Key를 붙인다. 내부 API를 노출한 서비스가 이 값으로 호출자를 인증한다.
@Component
public class InternalApiKeyInterceptor implements RequestInterceptor {

	private static final String API_KEY_HEADER = "X-Internal-Api-Key";

	private final String apiKey;

	public InternalApiKeyInterceptor(@Value("${internal.api-key}") String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public void apply(RequestTemplate template) {
		template.header(API_KEY_HEADER, apiKey);
	}
}
