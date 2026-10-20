package logistics.delivery.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import logistics.delivery.presentation.exception.DeliveryErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import logistics.delivery.presentation.dto.request.DeliveryAssignRequest;
import logistics.delivery.presentation.dto.request.DeliverySearchRequest;
import logistics.delivery.presentation.dto.request.DeliveryStatusUpdateRequest;
import logistics.delivery.presentation.dto.response.*;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Tag(name = "배송", description = "배송 및 배송 경로 관리 API")
public interface DeliveryApiSpec {

    @Operation(
            summary = "배송 목록 조회 및 검색",
            description = """
                MASTER는 전체 배송을 조회합니다.
                USER는 본인이 배송 또는 활성 구간 담당자인 배송만 조회합니다.
                검색 조건은 선택값이며, 여러 조건은 AND로 적용합니다.
                삭제된 배송은 제외합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "배송 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "검색값, 페이지 또는 정렬 조건 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "허용되지 않은 권한",
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
    @Parameters({
            @Parameter(
                    name = "X-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 사용자 UUID",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            @Parameter(
                    name = "X-Role-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 역할",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"USER", "MASTER"}
                    )
            )
    })
    ResponseEntity<Page<DeliverySearchResponse>> searchDeliveries(
            @ParameterObject
            @ModelAttribute DeliverySearchRequest searchRequest,

            @Parameter(
                    description = "페이지 번호, 0부터 시작",
                    schema = @Schema(minimum = "0", defaultValue = "0")
            )
            @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(
                    description = "페이지 크기, 1~100",
                    schema = @Schema(
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "10"
                    )
            )
            @RequestParam(name = "size", defaultValue = "10") int size,

            @Parameter(
                    description = """
                        정렬 필드와 방향.
                        필드: createdAt, updatedAt
                        방향: asc, desc
                        예: createdAt,desc
                        """,
                    schema = @Schema(defaultValue = "createdAt,desc")
            )
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,

            @Parameter(hidden = true)
            HttpServletRequest request
    );

    @Operation(
            summary = "배송 상세 조회",
            description = """
                MASTER : 전체 배송 조회 가능
                USER(배송 담당자) : 본인이 담당하는 배송만 조회 가능
                USER(주문자(요청 업체)) : 본인이 주문한 배송만 조회 가능
                삭제된 배송은 조회 대상에서 제외합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "배송 상세 조회 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryDetailResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 UUID 형식",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "허용되지 않은 권한",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 삭제됨 또는 조회 범위 밖",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "주문 서비스 장애로 주문자 확인 불가",
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
    @Parameters({
            @Parameter(
                    name = "X-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 사용자 UUID",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            @Parameter(
                    name = "X-Role-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 역할",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"USER", "MASTER"}
                    )
            )
    })
    ResponseEntity<DeliveryDetailResponse> getDeliveryDetail(
            @Parameter(description = "조회할 배송 ID", required = true)
            @PathVariable("id") UUID deliveryId,

            @Parameter(hidden = true)
            HttpServletRequest request
    );

    @Operation(
            summary = "배송 경로 조회",
            description = """
                MASTER : 전체 배송의 경로 조회 가능
                USER(배송 또는 구간 담당자) : 담당 배송의 전체 경로 조회 가능
                USER(주문자) : 본인이 주문한 배송의 전체 경로 조회 가능
                삭제된 배송과 경로는 조회 대상에서 제외합니다.
                경로 순서 오름차순으로 반환합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "배송 경로 조회 성공",
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = DeliveryRouteResponse.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 UUID 형식",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "허용되지 않은 권한",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 삭제됨 또는 조회 범위 밖",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "주문자 확인 불가 또는 주문 서비스 응답 오류",
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
    @Parameters({
            @Parameter(
                    name = "X-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 사용자 UUID",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            @Parameter(
                    name = "X-Role-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 역할",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"USER", "MASTER"}
                    )
            )
    })
    ResponseEntity<List<DeliveryRouteResponse>> getDeliveryRoutes(
            @Parameter(description = "조회할 배송 ID", required = true)
            @PathVariable("id") UUID deliveryId,
            @Parameter(hidden = true)
            HttpServletRequest request
    );

    @Operation(
            summary = "배송 경로 담당자 배정",
            description = """
                MASTER만 담당자를 배정할 수 있습니다.
                배송이 WAITING, HUB_IN_TRANSIT, HUB_ARRIVED이고
                대상 경로가 WAITING인 경우에만 허용합니다.
                기존 담당자를 다른 사용자로 변경하는 기능은 지원하지 않습니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "배정 성공 또는 동일 담당자 재요청",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryAssignResponse.class
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "요청 형식, 필수값 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "필수 사용자 정보 누락", content = @Content),
            @ApiResponse(responseCode = "403", description = "MASTER가 아닌 요청자", content = @Content),
            @ApiResponse(responseCode = "404", description = "배송, 배송 경로 없음 또는 삭제됨", content = @Content),
            @ApiResponse(responseCode = "409", description = "배정 불가 상태 또는 다른 담당자 배정됨", content = @Content),
            @ApiResponse(responseCode = "422", description = "배정 대상 사용자 없음 또는 자격, 소속 조건 불충족", content = @Content),
            @ApiResponse(responseCode = "503", description = "사용자 정보 검증 불가", content = @Content),
            @ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            )
    })
    @Parameters({
            @Parameter(
                    name = "X-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "배정을 요청하는 MASTER의 사용자 ID",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            @Parameter(
                    name = "X-Role-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"MASTER"}
                    )
            )
    })
    ResponseEntity<DeliveryAssignResponse> assignDeliveryManager(
            @Parameter(description = "배송 ID", required = true)
            @PathVariable("deliveryId") UUID deliveryId,

            @Parameter(description = "배정할 배송 경로 ID", required = true)
            @PathVariable("routeId") UUID routeId,

            @Valid @RequestBody DeliveryAssignRequest assignRequest,

            @Parameter(hidden = true)
            HttpServletRequest request
    );

    @Operation(
            summary = "배송 경로 상태 변경",
            description = """
                MASTER 또는 해당 배송 경로의 담당자만 변경할 수 있습니다.
                WAITING → IN_TRANSIT → ARRIVED 순서로 변경합니다.
                출발하려면 담당자가 배정되어 있어야 하며,
                이전 배송 경로가 있다면 모두 도착(ARRIVED)해야 합니다.
                배송 경로 상태가 실제로 변경되면 배송 전체 상태도 함께 갱신합니다.
                동일 상태 재요청은 변경 없이 현재 결과를 반환합니다.
                삭제되거나 취소된 배송 및 배송 경로는 변경할 수 없습니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "상태 변경 성공 또는 동일 상태 재요청",
                    content = @Content(
                            schema = @Schema(
                                    implementation = DeliveryStatusUpdateResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식, 필수값 오류 또는 허용하지 않는 요청 상태",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "MASTER 또는 해당 배송 경로 담당자가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송, 배송 경로 없음, 삭제됨 또는 배송 소속 불일치",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "상태 전이 불가, 담당자 미배정, 선행 배송 경로 미도착 또는 취소됨",
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
    @Parameters({
            @Parameter(
                    name = "X-User-Id",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "상태 변경 요청자의 사용자 ID",
                    schema = @Schema(type = "string", format = "uuid")
            ),
            @Parameter(
                    name = "X-Role-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"USER", "MASTER"}
                    )
            )
    })
    ResponseEntity<DeliveryStatusUpdateResponse> updateDeliveryStatus(
            @Parameter(description = "배송 ID", required = true)
            @PathVariable("deliveryId") UUID deliveryId,

            @Parameter(description = "배송 경로 ID", required = true)
            @PathVariable("routeId") UUID routeId,

            @Valid @RequestBody DeliveryStatusUpdateRequest updateRequest,

            @Parameter(hidden = true)
            HttpServletRequest request
    );
}