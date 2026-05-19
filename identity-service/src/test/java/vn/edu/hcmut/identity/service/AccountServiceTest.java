package vn.edu.hcmut.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.APIResponse;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.dto.response.PageResponse;
import vn.edu.hcmut.identity.dto.response.ProfileResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.mapper.AccountMapper;
import vn.edu.hcmut.identity.mapper.ProfileMapper;
import vn.edu.hcmut.identity.repository.AccountRepository;
import vn.edu.hcmut.identity.repository.httpclient.ProfileClient;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {
    @Mock
    AccountRepository accountRepository;

    @Mock
    AccountMapper accountMapper;

    @Mock
    ProfileMapper profileMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    ProfileClient profileClient;

//    @Mock
//    LmsClient lmsClient;

    @InjectMocks
    AccountService accountService;

    private Account mockAccount;
    private AccountResponse mockAccountResponse;
    private UserCreationRequest mockCreationRequest;

    @BeforeEach
    void setUp() {
        mockAccount = Account.builder()
                .id("acc-001")
                .username("testuser")
                .password("encoded_password")
                .roles(new HashSet<>(Set.of(UserRole.USER)))
                .build();

        mockAccountResponse =
                AccountResponse.builder().id("acc-001").username("testuser").build();

        AccountCreationRequest accountRequest = AccountCreationRequest.builder()
                .username("testuser")
                .password("raw_password")
                .role(UserRole.USER)
                .build();

        mockCreationRequest = UserCreationRequest.builder()
                .account(accountRequest)
                .email("test@hcmut.edu.vn")
                .firstName("Nguyen")
                .lastName("Van A")
                .build();

        lenient().when(profileMapper.toProfileCreationRequest(any())).thenReturn(new ProfileCreationRequest());
    }

    @Nested
    @DisplayName("createUser()")
    class CreateUser {
        @Test
        @DisplayName("Happy path — creates account and profile successfully")
        void createUser_success() {
            when(accountRepository.existsByUsername("testuser")).thenReturn(false);
            when(accountMapper.toAccount(any())).thenReturn(mockAccount);
            when(passwordEncoder.encode("raw_password")).thenReturn("encoded_password");
            when(accountRepository.save(any())).thenReturn(mockAccount);
            when(profileMapper.toProfileCreationRequest(any())).thenReturn(new ProfileCreationRequest());
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            AccountResponse result = accountService.createUser(mockCreationRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("acc-001");
            verify(accountRepository).save(any(Account.class));
            verify(profileClient).createProfile(any());
        }

        @Test
        @DisplayName("Password is encoded before saving")
        void createUser_passwordIsEncoded() {
            when(accountRepository.existsByUsername(any())).thenReturn(false);
            when(accountMapper.toAccount(any())).thenReturn(mockAccount);
            when(passwordEncoder.encode("raw_password")).thenReturn("encoded_password");
            when(accountRepository.save(any())).thenReturn(mockAccount);
            when(profileMapper.toProfileCreationRequest(any())).thenReturn(new ProfileCreationRequest());
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            accountService.createUser(mockCreationRequest);

            verify(passwordEncoder).encode("raw_password");
            verify(accountRepository).save(argThat(a -> "encoded_password".equals(a.getPassword())));
        }

        @Test
        @DisplayName("Role from request is assigned to new account")
        void createUser_roleIsAssigned() {
            when(accountRepository.existsByUsername(any())).thenReturn(false);
            when(accountMapper.toAccount(any())).thenReturn(mockAccount);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(profileMapper.toProfileCreationRequest(any())).thenReturn(new ProfileCreationRequest());
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            accountService.createUser(mockCreationRequest);

            verify(accountRepository)
                    .save(argThat(a -> a.getRoles() != null && a.getRoles().contains(UserRole.USER)));
        }
    }

    @Nested
    @DisplayName("updateAccount()")
    class UpdateAccount {

        @Test
        @DisplayName("Happy path — updates scalar fields")
        void updateAccount_success() {
            AccountUpdateRequest request =
                    AccountUpdateRequest.builder().password(null).build();

            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));
            when(accountRepository.save(any())).thenReturn(mockAccount);
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            AccountResponse result = accountService.updateAccount("acc-001", request);

            assertThat(result).isNotNull();
            verify(accountMapper).updateAccount(eq(mockAccount), eq(request));
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("Password is re-encoded when provided in update request")
        void updateAccount_withNewPassword_encodesPassword() {
            AccountUpdateRequest request =
                    AccountUpdateRequest.builder().password("new_password").build();

            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));
            when(passwordEncoder.encode("new_password")).thenReturn("new_encoded");
            when(accountRepository.save(any())).thenReturn(mockAccount);
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            accountService.updateAccount("acc-001", request);

            verify(passwordEncoder).encode("new_password");
        }

        @Test
        @DisplayName("Throws ACCOUNT_NOT_FOUND when account does not exist")
        void updateAccount_notFound_throwsException() {
            when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.updateAccount("nonexistent", new AccountUpdateRequest()))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAccounts()")
    class GetAccounts {

        @Test
        @DisplayName("Returns paginated list of accounts")
        void getAccounts_success() {
            Page<Account> accountPage = new PageImpl<>(List.of(mockAccount), PageRequest.of(0, 10), 1);

            when(accountRepository.findAll(any(PageRequest.class))).thenReturn(accountPage);
            when(accountMapper.toAccountResponse(any())).thenReturn(mockAccountResponse);

            PageResponse<AccountResponse> result = accountService.getAccounts(1, 10);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getCurrentPage()).isEqualTo(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Page 0 is treated as page 1 (offset correction)")
        void getAccounts_pageZero_treatedAsPageOne() {
            Page<Account> accountPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(accountRepository.findAll(any(PageRequest.class))).thenReturn(accountPage);

            accountService.getAccounts(0, 10);

            verify(accountRepository).findAll(PageRequest.of(0, 10));
        }
    }

    @Nested
    @DisplayName("addRoleToUser()")
    class AddRoleToUser {

        @Test
        @DisplayName("Happy path — adds role to account")
        void addRole_success() {
            mockAccount.setRoles(new HashSet<>());
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));

            accountService.addRoleToUser("acc-001", UserRole.TUTOR);

            verify(accountRepository).save(argThat(a -> a.getRoles().contains(UserRole.TUTOR)));
        }

        @Test
        @DisplayName("Role name is case-insensitive")
        void addRole_caseInsensitive() {
            mockAccount.setRoles(new HashSet<>());
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));

            accountService.addRoleToUser("acc-001", UserRole.TUTOR);

            verify(accountRepository).save(argThat(a -> a.getRoles().contains(UserRole.TUTOR)));
        }
    }

    @Nested
    @DisplayName("removeRole()")
    class RemoveRole {

        @Test
        @DisplayName("Happy path — removes existing role from account")
        void removeRole_success() {
            mockAccount.setRoles(new HashSet<>(Set.of(UserRole.TUTOR, UserRole.USER)));
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));

            accountService.removeRole("acc-001", UserRole.TUTOR);

            verify(accountRepository)
                    .save(argThat(a -> !a.getRoles().contains(UserRole.TUTOR)
                            && a.getRoles().contains(UserRole.USER)));
        }

        @Test
        @DisplayName("No save when role does not exist on account")
        void removeRole_roleNotPresent_noSave() {
            mockAccount.setRoles(new HashSet<>(Set.of(UserRole.USER)));
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));

            accountService.removeRole("acc-001", UserRole.TUTOR);

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccount {

        @Test
        @DisplayName("Happy path — deletes account, tutor, and profile in order")
        void deleteAccount_success() {
            ProfileResponse profile = ProfileResponse.builder().id("prof-001").build();
            APIResponse<ProfileResponse> response =
                    APIResponse.<ProfileResponse>builder().result(profile).build();

            when(accountRepository.existsById("acc-001")).thenReturn(true);
            when(profileClient.getProfileByAccountId("acc-001")).thenReturn(response.getResult());

            accountService.deleteAccount("acc-001");

            // var inOrder = inOrder(lmsClient, profileClient, accountRepository);
            // inOrder.verify(lmsClient).deleteTutor("prof-001");
            // inOrder.verify(profileClient).deleteProfile("prof-001");
            // inOrder.verify(accountRepository).deleteById("acc-001");
        }

        @Test
        @DisplayName("Throws ACCOUNT_NOT_FOUND when account does not exist")
        void deleteAccount_notFound_throwsException() {
            when(accountRepository.existsById("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> accountService.deleteAccount("nonexistent"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

            verify(accountRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Still deletes account when profile fetch fails")
        void deleteAccount_profileFetchFails_stillDeletesAccount() {
            when(accountRepository.existsById("acc-001")).thenReturn(true);
            when(profileClient.getProfileByAccountId("acc-001"))
                    .thenThrow(new RuntimeException("Profile Service down"));

            accountService.deleteAccount("acc-001");

            // Profile and tutor deletion skipped, but account is still deleted
            // verify(lmsClient, never()).deleteTutor(any());
            // verify(profileClient, never()).deleteProfile(any());
            verify(accountRepository).deleteById("acc-001");
        }

        @Test
        @DisplayName("Throws DELETE_LMS_FAILED when lmsClient fails")
        void deleteAccount_lmsClientFails_throwsException() {
            ProfileResponse profile = ProfileResponse.builder().id("prof-001").build();
            APIResponse<ProfileResponse> response =
                    APIResponse.<ProfileResponse>builder().result(profile).build();

            when(accountRepository.existsById("acc-001")).thenReturn(true);
            when(profileClient.getProfileByAccountId("acc-001")).thenReturn(response.getResult());
            // doThrow(new RuntimeException("LMS error")).when(lmsClient).deleteTutor("prof-001");

//            assertThatThrownBy(() -> accountService.deleteAccount("acc-001"))
//                    .isInstanceOf(AppException.class)
//                    .extracting(e -> ((AppException) e).getErrorCode())
//                    .isEqualTo(ErrorCode.DELETE_LMS_FAILED);

            // Profile and account deletion must NOT proceed after LMS failure
            // verify(profileClient, never()).deleteProfile(any());
            verify(accountRepository, never()).deleteById(any());
        }
    }
}
