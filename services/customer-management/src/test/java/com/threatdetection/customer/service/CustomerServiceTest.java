package com.threatdetection.customer.service;

import com.threatdetection.customer.dto.CreateCustomerRequest;
import com.threatdetection.customer.exception.CustomerAdminProvisioningException;
import com.threatdetection.customer.exception.CustomerOperationConflictException;
import com.threatdetection.customer.model.Customer;
import com.threatdetection.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, jdbcTemplate, passwordEncoder);
    }

    @Test
    void createPersistsTenantAndUsesBackendLifecycleDefaults() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("customer-a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .build();
        when(customerRepository.existsByCustomerId("customer-a")).thenReturn(false);
        allowTenantCreation(10, 0);
        when(customerRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    customer.setId(1L);
                    return customer;
                });

        customerService.createCustomer(request);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(23L);
        assertThat(captor.getValue().getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
        assertThat(captor.getValue().getSubscriptionTier()).isEqualTo(Customer.SubscriptionTier.BASIC);
    }

    @Test
    void customerAndOptionalLoginAreProvisionedInOneServiceTransaction() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("customerA")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .createAdmin(true)
                .adminUsername("customerA-admin")
                .adminPassword("securepass1234")
                .build();
        when(customerRepository.existsByCustomerId("customerA")).thenReturn(false);
        allowTenantCreation(10, 0);
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(passwordEncoder.encode("securepass1234")).thenReturn("bcrypt-hash");
        when(jdbcTemplate.queryForObject(contains("INSERT INTO auth_users"), eq(Long.class), any(Object[].class)))
                .thenReturn(91L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        customerService.createCustomer(request);

        verify(passwordEncoder).encode("securepass1234");
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void eightCharacterCustomerAdministratorPasswordIsAccepted() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .createAdmin(true)
                .adminUsername("a-admin")
                .adminPassword("12345678")
                .build();
        when(customerRepository.existsByCustomerId("a")).thenReturn(false);
        allowTenantCreation(10, 0);
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(passwordEncoder.encode("12345678")).thenReturn("bcrypt-hash");
        when(jdbcTemplate.queryForObject(contains("INSERT INTO auth_users"), eq(Long.class), any(Object[].class)))
                .thenReturn(91L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        customerService.createCustomer(request);

        verify(passwordEncoder).encode("12345678");
    }

    @Test
    void sevenCharacterCustomerAdministratorPasswordIsRejected() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .createAdmin(true)
                .adminUsername("a-admin")
                .adminPassword("1234567")
                .build();
        when(customerRepository.existsByCustomerId("a")).thenReturn(false);
        allowTenantCreation(10, 0);
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(CustomerAdminProvisioningException.class)
                .hasMessageContaining("8-16");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void relatedAdministratorUsernameIsEnforcedBeforeAccountInsert() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("customerA")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .createAdmin(true)
                .adminUsername("unrelated-admin")
                .adminPassword("securepass1234")
                .build();
        when(customerRepository.existsByCustomerId("customerA")).thenReturn(false);
        allowTenantCreation(10, 0);
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(CustomerAdminProvisioningException.class)
                .hasMessageContaining("customerA-admin");
    }

    @Test
    void inactiveTenantCannotReceiveNewCustomers() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("customer-a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .build();
        when(jdbcTemplate.queryForList(contains("FROM tenants"), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", "INACTIVE", "max_customers", 10)));

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("active tenant");
        verify(customerRepository, never()).saveAndFlush(any(Customer.class));
    }

    @Test
    void tenantCustomerLimitIsEnforcedBeforeInsert() {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .customerId("customer-a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .build();
        allowTenantCreation(2, 2);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("customer limit");
        verify(customerRepository, never()).saveAndFlush(any(Customer.class));
    }

    @Test
    void tenantScopedListCannotFallBackToGlobalList() {
        PageRequest page = PageRequest.of(0, 20);
        Customer customer = Customer.builder()
                .customerId("customer-a")
                .tenantId(23L)
                .name("Customer A")
                .email("a@example.com")
                .build();
        when(customerRepository.findByTenantId(23L, page))
                .thenReturn(new PageImpl<>(List.of(customer)));

        assertThat(customerService.getAllCustomers(23L, null, page).getContent())
                .extracting("customerId")
                .containsExactly("customer-a");
        verify(customerRepository).findByTenantId(23L, page);
    }

    @Test
    void softDeleteArchivesCustomerInsteadOfRemovingIdentity() {
        Customer customer = Customer.builder()
                .customerId("customer-a")
                .name("Customer A")
                .email("a@example.com")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();
        when(customerRepository.findByCustomerId("customer-a"))
                .thenReturn(Optional.of(customer));

        customerService.deleteCustomer("customer-a");

        assertThat(customer.getStatus()).isEqualTo(Customer.CustomerStatus.INACTIVE);
        verify(customerRepository).save(customer);
    }

    @Test
    void permanentDeleteRequiresArchivedCustomer() {
        Customer customer = Customer.builder()
                .customerId("customer-a")
                .name("Customer A")
                .email("a@example.com")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();
        when(customerRepository.findByCustomerId("customer-a"))
                .thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.hardDeleteCustomer("customer-a"))
                .isInstanceOf(CustomerOperationConflictException.class)
                .hasMessageContaining("archived");
    }

    private void allowTenantCreation(int maxCustomers, long currentCustomers) {
        when(jdbcTemplate.queryForList(contains("FROM tenants"), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "max_customers", maxCustomers)));
        when(jdbcTemplate.queryForObject(
                contains("COUNT(*) FROM customers"), eq(Long.class), any(Object[].class)))
                .thenReturn(currentCustomers);
    }
}
