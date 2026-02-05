package vn.edu.hcmut.identity.mapper;

import org.mapstruct.*;

import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.entity.Account;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    @Mapping(target = "id", ignore = true)
    Account toAccount(AccountCreationRequest request);

    AccountResponse toAccountResponse(Account account);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateAccount(@MappingTarget Account account, AccountUpdateRequest request);
}
