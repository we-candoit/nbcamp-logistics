package logistics.user.infrastructure.client;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CompanyResponse {

    private UUID companyId;
    private String name;
}