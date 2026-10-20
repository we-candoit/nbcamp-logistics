package logistics.user.infrastructure.client;

import lombok.Getter;

import java.util.List;

@Getter
public class CompanyPageResponse {

    private List<CompanyResponse> content;
}