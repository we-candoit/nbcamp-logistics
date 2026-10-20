package logistics.hub.common;

import java.util.UUID;

public final class TemporaryUserIds {

    private TemporaryUserIds() {
    }

    // User Service 통합 전까지 사용할 임시 MASTER 사용자 ID
    public static final UUID LOCAL_MASTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
}