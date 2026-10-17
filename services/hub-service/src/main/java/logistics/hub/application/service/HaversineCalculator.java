package logistics.hub.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVERAGE_SPEED_KM_H = 60.0;

    private HaversineCalculator() {
    }

    public static BigDecimal calculateDistanceKm( // 출발/도착지점의 위도, 경도
            double originLatitude, double originLongitude,
            double destinationLatitude, double destinationLongitude
    ) {
        double lat1Rad = Math.toRadians(originLatitude);
        double lat2Rad = Math.toRadians(destinationLatitude);
        double latDiff = lat2Rad - lat1Rad;
        double lonDiff = Math.toRadians(destinationLongitude - originLongitude);

        double a = Math.pow(Math.sin(latDiff / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.pow(Math.sin(lonDiff / 2), 2);

        // 부동소수점 오차로 sqrt(1-a)가 음수가 되는 상황을 방지한다
        a = Math.max(0.0, Math.min(1.0, a));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }

    // 직선거리와 가정한 속도(80km)로 계산한 값
    public static int estimateDurationMinutes(BigDecimal distanceKm) {
        return Math.max(1, (int) Math.ceil(distanceKm.doubleValue() / AVERAGE_SPEED_KM_H * 60));
    }
}
