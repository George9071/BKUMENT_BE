package vn.edu.hcmut.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.entity.Account;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    Account toAccount(AccountCreationRequest request);

    AccountResponse toAccountResponse(Account account);

    void updateAccount(@MappingTarget Account account, AccountUpdateRequest request);
}
