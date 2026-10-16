package logistics.delivery.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(name = "배송", description = "배송 및 배송 경로 관리 API")
public interface DeliveryApiSpec {

    @Operation(
            summary = "배송 생성",
            description = """
                    주문 서비스가 호출하는 내부 API로
                    배송과 배송 경로를 함께 생성합니다

                    같은 허브: 허브 → 업체 1구간
                    다른 허브: 허브 → 허브, 허브 → 업체 2구간

                    같은 주문의 동일 배송 생성 요청은 기존 배송을 반환하고,
                    생성 내용이 다른 요청은 거절합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "신규 배송 생성 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryCreateResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "동일 생성 요청으로 이미 생성된 배송 반환",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryCreateResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "내부 서비스 인증 실패",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "동일 주문의 생성 내용 충돌",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "연동 서비스 장애로 배송 생성 불가",
                    content = @Content
            )
    })
    ResponseEntity<DeliveryCreateResponse> createDelivery(
            @Valid @RequestBody DeliveryCreateRequest request
    );

    @Operation(
            summary = "배송 취소",
            description = """
                주문 서비스가 호출하는 내부 API로,
                출발 전 배송과 소속 경로를 함께 취소합니다.
                이미 취소된 배송은 변경 없이 기존 결과를 반환합니다.
                재고 복원이나 논리 삭제는 수행하지 않습니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "배송 취소 성공 또는 이미 취소된 배송",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryCancelResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 배송 ID",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 또는 역할 헤더 누락, 공백",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 논리 삭제된 상태",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "배송 또는 배송 경로가 이미 출발한 상태라 취소 불가",
                    content = @Content
            )
    })
    ResponseEntity<DeliveryCancelResponse> cancelDelivery(
            @Parameter(description = "취소할 배송 ID", required = true)
            @PathVariable("id") UUID deliveryId
    );





}