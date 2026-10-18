package logistics.delivery.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import logistics.delivery.presentation.dto.response.DeliveryDetailResponse;
import logistics.delivery.presentation.dto.response.DeliveryRouteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@Tag(name = "배송", description = "배송 및 배송 경로 관리 API")
public interface DeliveryApiSpec {

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
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "허용되지 않은 권한",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 삭제됨 또는 조회 범위 밖",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "주문 서비스 장애로 주문자 확인 불가",
                    content = @Content
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
                    name = "X-User-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 username"
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
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "필수 사용자 정보 누락",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "허용되지 않은 권한",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송이 없거나 삭제됨 또는 조회 범위 밖",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "주문자 확인 불가 또는 주문 서비스 응답 오류",
                    content = @Content
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
                    name = "X-User-Name",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "조회 요청자의 username"
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
}