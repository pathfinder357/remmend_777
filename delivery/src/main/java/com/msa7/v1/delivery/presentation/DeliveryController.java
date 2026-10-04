package com.msa7.v1.delivery.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.msa7.v1.delivery.app.DeliveryManagerService;
import com.msa7.v1.delivery.app.DeliveryService;
import com.msa7.v1.delivery.domain.vo.RouteStatus;
import com.msa7.v1.delivery.presentation.dto.CreateDeliveryRequest;
import com.msa7.v1.delivery.presentation.dto.CreateManagerRequest;
import com.msa7.v1.delivery.presentation.dto.RestApiResponse;
import com.msa7.v1.delivery.presentation.dto.RouteStatusUpdateRequest;
import com.msa7.v1.delivery.presentation.dto.UpdateDeliveryStatusRequest;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryResponse;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryRouteResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

	private final DeliveryService deliveryService;
	private final DeliveryManagerService deliveryManagerService;

	// 배송 담당자 등록
	@PreAuthorize("hasRole('MASTER')")
	@PostMapping("/managers")
	public ResponseEntity<RestApiResponse<UUID>> createManager(
		@RequestHeader("X-User-Id") UUID userId,
		@RequestBody CreateManagerRequest request) {

		// 요청된 사용자 ID를 기반으로 생성
		UUID id = deliveryManagerService.createDeliveryManager(request.userId(), request.slackId(), request.hubId(), request.type());
		return ResponseEntity.ok(RestApiResponse.ok( "배송 담당자가 생성되었습니다.", id));
	}


	// 배송 담당자는 본인에게 배정된 경로만, 관리자는 모든 경로를 변경할 수 있다.
	@PreAuthorize("hasAnyRole('MASTER', 'HUB_MANAGER', 'DELIVERY_AGENT')")
	@PatchMapping("/{deliveryId}/routes/{routeId}/status")
	public ResponseEntity<RestApiResponse<Void>> updateRouteStatus(
		@PathVariable UUID deliveryId,
		@PathVariable UUID routeId,
		@RequestBody RouteStatusUpdateRequest request,
		Authentication authentication
	) {
		deliveryService.updateDeliveryRouteStatus(deliveryId, routeId, request.status(),
			ownershipCheckTarget(authentication));
		return ResponseEntity.ok(RestApiResponse.ok("배송상태 업데이트", null));
	}

	/*
	 * 담당자 본인 확인이 필요한 요청이면 사용자 ID를, 관리자 요청이면 null을 돌려준다.
	 * null이면 서비스가 담당자 확인을 생략한다.
	 */
	private UUID ownershipCheckTarget(Authentication authentication) {
		boolean isAdmin = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(role -> role.equals("ROLE_MASTER") || role.equals("ROLE_HUB_MANAGER"));

		return isAdmin ? null : (UUID) authentication.getPrincipal();
	}


	// 배송 상태 변경
	@PreAuthorize("hasAnyRole('MASTER', 'HUB_MANAGER', 'DELIVERY_AGENT')")
	@PatchMapping("/{deliveryId}/status")
	public ResponseEntity<RestApiResponse<Void>> updateStatus(
		@PathVariable UUID deliveryId,
		@RequestBody UpdateDeliveryStatusRequest request) {

		deliveryService.updateDeliveryStatus(deliveryId, request.status());

		return ResponseEntity.ok(RestApiResponse.ok( "배송 상태가 업데이트되었습니다." , null));
	}

	@PreAuthorize("hasAnyRole('MASTER', 'HUB_MANAGER')")
	@DeleteMapping("/{id}")
	public ResponseEntity<RestApiResponse<Void>> deleteDelivery(
		@RequestHeader("X-User-Id") UUID deletedBy,
		@PathVariable UUID id) {

		deliveryService.deleteDelivery(id, deletedBy);
		return ResponseEntity.ok(RestApiResponse.ok( "배송이 삭제되었습니다.", null));
	}

	@PreAuthorize("hasAnyRole('MASTER', 'HUB_MANAGER')")
	@GetMapping("/routes")
	public ResponseEntity<RestApiResponse<List<DeliveryRouteResponse>>> getDeliveryRoutes(
		@RequestParam UUID hubId,
		@RequestParam RouteStatus status) {

		List<DeliveryRouteResponse> responses = deliveryService.getDeliveryRoutes(hubId, status);
		return ResponseEntity.ok(RestApiResponse.ok( "배송 경로 조회가 완료되었습니다.", responses));
	}

	@PreAuthorize("hasAnyRole('MASTER', 'HUB_MANAGER', 'DELIVERY_AGENT', 'SUPPLIER_AGENT')")
	@GetMapping("/{deliveryId}")
	public ResponseEntity<RestApiResponse<DeliveryResponse>> getDeliveryInfo(
		@PathVariable UUID deliveryId) {

		DeliveryResponse response = deliveryService.getDeliveryInfo(deliveryId);
		return ResponseEntity.ok(RestApiResponse.ok( "배송 단건 조회가 완료되었습니다.", response));
	}
}