package com.sparta.productservice.presentation.inventory;

import com.sparta.productservice.application.inventory.InventoryService;
import com.sparta.productservice.domain.inventory.InventoryChangeType;
import com.sparta.productservice.presentation.inventory.request.InventoryQuantityChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/*
 * order-service 전용 내부 재고 API.
 *
 * Gateway 라우팅 대상이 아니며, InternalApiKeyFilter가 X-Internal-Api-Key를 검증한 요청만 도달한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/inventories")
public class InventoryInternalController {

    private final InventoryService inventoryService;

    /*
     * 주문 생성 시 재고를 차감한다.
     *
     * 재고가 부족하면 InventoryErrorCode.INSUFFICIENT_INVENTORY(409)가 발생하고,
     * 호출자인 order-service의 주문 생성 트랜잭션이 함께 실패한다.
     */
    @PostMapping("/{productId}/decrease")
    public ResponseEntity<Void> decreaseInventory(
            @PathVariable("productId") UUID productId,
            @RequestParam("quantity") int quantity) {

        inventoryService.changeQuantity(
                productId,
                new InventoryQuantityChangeRequest(
                        InventoryChangeType.ORDER_DECREASE,
                        quantity
                )
        );

        return ResponseEntity.noContent().build();
    }

    // 주문이 취소되거나 주문 트랜잭션이 롤백되었을 때 차감했던 재고를 되돌린다.
    @PostMapping("/{productId}/restore")
    public ResponseEntity<Void> restoreInventory(
            @PathVariable("productId") UUID productId,
            @RequestParam("quantity") int quantity) {

        inventoryService.changeQuantity(
                productId,
                new InventoryQuantityChangeRequest(
                        InventoryChangeType.ORDER_CANCEL_RESTORE,
                        quantity
                )
        );

        return ResponseEntity.noContent().build();
    }
}
