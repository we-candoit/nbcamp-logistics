package logistics.company.presentation.dto.request.company;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompanySearchCondition {
    private String companyName;                 // 업체명 (부분 일치)
    private String address;                     // 업체 주소 (부분 일치)
}
