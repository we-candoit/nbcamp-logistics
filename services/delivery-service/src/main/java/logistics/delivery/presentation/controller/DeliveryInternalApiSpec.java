package logistics.delivery.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import logistics.delivery.presentation.exception.DeliveryErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import logistics.delivery.presentation.dto.request.DeliveryCreateRequest;
import logistics.delivery.presentation.dto.response.DeliveryCancelResponse;
import logistics.delivery.presentation.dto.response.DeliveryCreateResponse;
import logistics.delivery.presentation.dto.response.DeliveryOrderCancelResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

public interface DeliveryInternalApiSpec {

    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true,
            description = "요청자의 사용자 ID", schema = @Schema(type = "string", format = "uuid"))
    @Parameter(name = "X-Role-Name", in = ParameterIn.HEADER, required = true,
            description = "요청자의 역할")
    @Operation(
            summary = "배송 생성",
            description = """
                    주문 서비스가 호출하는 내부 API로,
                    배송과 배송 경로를 함께 생성합니다

                    같은 허브: 허브 → 업체 1구간
                    다른 허브: 허브 → 허브, 허브 → 업체 2구간

                    같은 주문의 동일 배송 생성 요청은 기존 배송을 반환하고,
                    생성 내용이 다른 요청은 거절합니다.
                    취소 또는 삭제 처리된 주문은 배송 생성이 차단됩니다.
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
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 ID 또는 역할 헤더 누락, 공백",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "동일 주문의 생성 내용 충돌 또는 취소·삭제에 따른 생성 차단",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "연동 서비스 장애로 배송 생성 불가",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            )
    })
    ResponseEntity<DeliveryCreateResponse> createDelivery(
            @Valid @RequestBody DeliveryCreateRequest request
    );

    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true,
            description = "요청자의 사용자 ID", schema = @Schema(type = "string", format = "uuid"))
    @Parameter(name = "X-Role-Name", in = ParameterIn.HEADER, required = true,
            description = "요청자의 역할")
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
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 ID 또는 역할 헤더 누락, 공백",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 논리 삭제된 상태",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "배송 또는 배송 경로가 이미 출발한 상태라 취소 불가",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            )
    })
    ResponseEntity<DeliveryCancelResponse> cancelDelivery(
            @Parameter(description = "취소할 배송 ID", required = true)
            @PathVariable("id") UUID deliveryId
    );

    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true,
            description = "요청자의 사용자 ID", schema = @Schema(type = "string", format = "uuid"))
    @Parameter(name = "X-Role-Name", in = ParameterIn.HEADER, required = true,
            description = "요청자의 역할")
    @Operation(
            summary = "배송 삭제",
            description = """
                주문 서비스가 호출하는 내부 API로,
                취소된 배송과 소속 경로에 삭제 시간과 삭제 요청자를 기록합니다.
                실제 DB 데이터를 삭제하지 않는 Soft Delete입니다.
                이미 삭제된 배송은 최초 삭제 기록을 유지합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "논리 삭제 성공 또는 이미 삭제된 배송",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 배송 ID 형식",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 ID 또는 역할 헤더 누락, 공백",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 존재하지 않음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "취소되지 않은 배송은 삭제 불가",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            )
    })
    ResponseEntity<Void> deleteDelivery(
            @Parameter(description = "삭제할 배송 ID", required = true)
            @PathVariable("id") UUID deliveryId
    );

    @Parameter(
            name = "X-User-Id",
            in = ParameterIn.HEADER,
            required = true,
            description = "요청자의 사용자 ID",
            schema = @Schema(type = "string", format = "uuid")
    )
    @Parameter(
            name = "X-Role-Name",
            in = ParameterIn.HEADER,
            required = true,
            description = "요청자의 역할"
    )
    @Operation(
            summary = "주문 ID로 배송 취소",
            description = """
                주문 서비스가 호출하는 내부 API로,
                주문 ID로 배송을 찾아 출발 전 배송과 배송 경로를 함께 취소합니다.
                이미 취소된 배송은 변경 없이 기존 결과를 반환합니다.
                배송이 없으면 취소 요청을 관리 기록에 저장하고 CREATION_BLOCKED를 반환합니다.
                기존 배송을 취소한 경우 CANCELLED를 반환합니다.
                두 결과 모두 HTTP 200이며, 생성 차단 결과의 deliveryId는 null입니다.
                관리 기록이 이미 DELETED라면 해당 상태를 유지합니다.
                실제 배송이 논리 삭제된 상태라면 기존 정책대로 404를 반환합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "기존 배송 취소 또는 배송 없는 주문의 생성 차단 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryOrderCancelResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 주문 ID 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 ID 또는 역할 헤더 누락, 공백",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 주문의 실제 배송이 논리 삭제된 상태",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "배송 또는 배송 경로가 이미 출발하여 취소 불가",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<DeliveryOrderCancelResponse> cancelDeliveryByOrderId(
            @Parameter(description = "취소할 배송과 연결된 주문 ID", required = true)
            @PathVariable("orderId") UUID orderId
    );

    @Parameter(
            name = "X-User-Id",
            in = ParameterIn.HEADER,
            required = true,
            description = "요청자의 사용자 ID",
            schema = @Schema(type = "string", format = "uuid")
    )
    @Parameter(
            name = "X-Role-Name",
            in = ParameterIn.HEADER,
            required = true,
            description = "요청자의 역할"
    )
    @Operation(
            summary = "주문 ID로 배송 삭제",
            description = """
                주문 서비스가 호출하는 내부 API로,
                주문 ID로 배송을 찾아 취소된 배송과 배송 경로를 함께 삭제합니다.
                삭제 시각과 요청자의 사용자 ID를 기록합니다.
                이미 삭제된 배송은 최초 삭제 기록을 유지하고 204를 반환합니다.
                배송이 없어도 취소 관리 기록이 있으면 삭제 완료로 처리하고 204를 반환합니다.
                배송도 없고 취소 기록도 없다면 취소를 먼저 요청하도록 409를 반환합니다.
                삭제 후에도 관리 기록을 유지하여 이후 배송 생성을 차단합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "논리 삭제 또는 취소 관리 기록의 삭제 처리 완료, 이미 삭제된 경우 포함",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 주문 ID 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "사용자 ID 또는 역할 헤더 누락, 공백",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "취소되지 않은 배송 또는 배송 없이 취소 기록도 없는 주문은 삭제 불가",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<Void> deleteDeliveryByOrderId(
            @Parameter(description = "삭제할 배송과 연결된 주문 ID", required = true)
            @PathVariable("orderId") UUID orderId
    );

}
