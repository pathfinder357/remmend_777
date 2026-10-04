package com.msa7.v1.order.infra.feign;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface InventoryClient {
	// 주문 생성 시 재고 차감. 재고가 부족하면 product-service가 409를 반환한다.
	@PostMapping("/internal/inventories/{productId}/decrease")
	void decreaseInventory(@PathVariable("productId") UUID productId, @RequestParam("quantity") Integer quantity);

	// 주문 취소 또는 주문 트랜잭션 롤백 시 차감했던 재고를 복구한다.
	@PostMapping("/internal/inventories/{productId}/restore")
	void restoreInventory(@PathVariable("productId") UUID productId, @RequestParam("quantity") Integer quantity);
}
