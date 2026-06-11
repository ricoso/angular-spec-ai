package com.autohaus.backend;

import com.autohaus.backend.branches.BranchesController;
import com.autohaus.backend.branches.BranchesService;
import com.autohaus.backend.branches.dto.BranchResponseDto;
import com.autohaus.backend.brands.BrandsController;
import com.autohaus.backend.brands.BrandsService;
import com.autohaus.backend.brands.dto.BrandResponseDto;
import com.autohaus.backend.health.HealthController;
import com.autohaus.backend.health.HealthService;
import com.autohaus.backend.jobtypes.JobTypesController;
import com.autohaus.backend.jobtypes.JobTypesService;
import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import com.autohaus.backend.mobility.MobilityController;
import com.autohaus.backend.mobility.MobilityService;
import com.autohaus.backend.mobility.dto.MobilityAlternativeResponseDto;
import com.autohaus.backend.rentcategories.RentCategoriesController;
import com.autohaus.backend.rentcategories.RentCategoriesService;
import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import com.autohaus.backend.cars.CarsController;
import com.autohaus.backend.cars.CarsService;
import com.autohaus.backend.cars.dto.CarResponseDto;
import com.autohaus.backend.cars.dto.CreateCarRequest;
import com.autohaus.backend.customers.CustomersController;
import com.autohaus.backend.customers.CustomersService;
import com.autohaus.backend.customers.dto.CreateCustomerRequest;
import com.autohaus.backend.customers.dto.CustomerResponseDto;
import com.autohaus.backend.appointments.AppointmentsController;
import com.autohaus.backend.appointments.AppointmentsService;
import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.snaps.SnapsController;
import com.autohaus.backend.snaps.SnapsService;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.session.SessionController;
import com.autohaus.backend.session.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerTests {

    @Mock private BranchesService branchesService;
    @Mock private BrandsService brandsService;
    @Mock private JobTypesService jobTypesService;
    @Mock private MobilityService mobilityService;
    @Mock private RentCategoriesService rentCategoriesService;
    @Mock private CarsService carsService;
    @Mock private CustomersService customersService;
    @Mock private AppointmentsService appointmentsService;
    @Mock private SnapsService snapsService;
    @Mock private HealthService healthService;
    @Mock private SessionTokenService sessionTokenService;

    // === BranchesController ===

    @Test
    void branchesController_getBranches_returnsList() {
        BranchesController ctrl = new BranchesController(branchesService);
        when(branchesService.getBranches()).thenReturn(List.of(
            new BranchResponseDto("1", "Munich", "desc", null, List.of("BMW", "Audi"), null)
        ));
        List<BranchResponseDto> result = ctrl.getBranches();
        assertEquals(1, result.size());
        assertEquals("Munich", result.getFirst().name());
    }

    @Test
    void branchesController_getBranches_emptyList() {
        BranchesController ctrl = new BranchesController(branchesService);
        when(branchesService.getBranches()).thenReturn(List.of());
        assertTrue(ctrl.getBranches().isEmpty());
    }

    @Test
    void branchesController_getBranch_returnsBranch() {
        BranchesController ctrl = new BranchesController(branchesService);
        when(branchesService.getBranch("1")).thenReturn(new BranchResponseDto("1", "Test", "desc", null, List.of(), null));
        BranchResponseDto result = ctrl.getBranch("1");
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("Test", result.name());
    }

    // === BrandsController ===

    @Test
    void brandsController_getBrands_returnsList() {
        BrandsController ctrl = new BrandsController(brandsService);
        when(brandsService.getBrandsByBranch("b1")).thenReturn(List.of(
            new BrandResponseDto("1", "BMW"),
            new BrandResponseDto("2", "Audi")
        ));
        List<BrandResponseDto> result = ctrl.getBrands("b1");
        assertEquals(2, result.size());
    }

    @Test
    void brandsController_getBrands_emptyList() {
        BrandsController ctrl = new BrandsController(brandsService);
        when(brandsService.getBrandsByBranch("b1")).thenReturn(List.of());
        assertTrue(ctrl.getBrands("b1").isEmpty());
    }

    // === JobTypesController ===

    @Test
    void jobTypesController_getJobTypes_returnsList() {
        JobTypesController ctrl = new JobTypesController(jobTypesService);
        when(jobTypesService.getJobTypesByBranch("b1", null)).thenReturn(List.of(
            new JobTypeResponseDto("1", "Oil Change", "desc", null, null, null, null, List.of())
        ));
        assertEquals(1, ctrl.getJobTypes("b1", null).size());
    }

    @Test
    void jobTypesController_getJobTypes_emptyList() {
        JobTypesController ctrl = new JobTypesController(jobTypesService);
        when(jobTypesService.getJobTypesByBranch("b1", null)).thenReturn(List.of());
        assertTrue(ctrl.getJobTypes("b1", null).isEmpty());
    }

    // === MobilityController ===

    @Test
    void mobilityController_getMobilityAlternatives_returnsList() {
        MobilityController ctrl = new MobilityController(mobilityService);
        when(mobilityService.getMobilityAlternatives("b1")).thenReturn(List.of(
            new MobilityAlternativeResponseDto("1", "Rental Car")
        ));
        List<MobilityAlternativeResponseDto> result = ctrl.getMobilityAlternatives("b1");
        assertEquals(1, result.size());
        assertEquals("Rental Car", result.getFirst().name());
    }

    // === CarsController ===

    @Test
    void carsController_createCar_returnsCarResponse() {
        CarsController ctrl = new CarsController(carsService);
        when(carsService.createCar(any())).thenReturn(new CarResponseDto("1", "B-MS1234"));
        CarResponseDto result = ctrl.createCar(new CreateCarRequest("B-MS1234", 50000, "WVWZZZ3CZWE123456", null, "branch-1"));
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("B-MS1234", result.licensePlate());
    }

    // === CustomersController ===

    @Test
    void customersController_createCustomer_returnsCustomerResponse() {
        CustomersController ctrl = new CustomersController(customersService);
        when(customersService.createCustomer(any())).thenReturn(new CustomerResponseDto("1", "a@b.de", "Max", "Muster"));
        CustomerResponseDto result = ctrl.createCustomer(new CreateCustomerRequest(
            "a@b.de", "mr", "Max", "Muster", "Str 1", "12345", "Berlin", "+491701234567", "branch-1"
        ));
        assertNotNull(result);
        assertEquals("Max", result.firstName());
    }

    @Test
    void customersController_searchCustomers_returnsList() {
        CustomersController ctrl = new CustomersController(customersService);
        when(customersService.searchCustomers(null, null, "test@test.de", "branch-1")).thenReturn(List.of(
            new CustomerResponseDto("1", "test@test.de", "Test", "User")
        ));
        List<CustomerResponseDto> result = ctrl.searchCustomers(null, null, "test@test.de", "branch-1");
        assertEquals(1, result.size());
    }

    @Test
    void customersController_searchCustomers_emptyResult() {
        CustomersController ctrl = new CustomersController(customersService);
        when(customersService.searchCustomers("B-MS1234", null, null, "branch-1")).thenReturn(List.of());
        assertTrue(ctrl.searchCustomers("B-MS1234", null, null, "branch-1").isEmpty());
    }

    // === AppointmentsController ===

    @Test
    void appointmentsController_createAppointment_returnsResponse() {
        AppointmentsController ctrl = new AppointmentsController(appointmentsService);
        AppointmentResponseDto dto = new AppointmentResponseDto("1", "confirmed", "2026-04-15", "14:30", "Branch", List.of("Oil Change"), "Max M", "B-MS1234");
        when(appointmentsService.createAppointment(any())).thenReturn(dto);

        CreateAppointmentRequest.CustomerDto customer = new CreateAppointmentRequest.CustomerDto(
            "a@b.de", "Max", "M", "+49170", null, "Str 1", "12345", "Berlin", null, null, "mr"
        );
        CreateAppointmentRequest.VehicleDto vehicle = new CreateAppointmentRequest.VehicleDto(
            "B-MS1234", 50000, null, null, null, null
        );
        CreateAppointmentRequest request = new CreateAppointmentRequest("snap1", "branch-1", null, customer, vehicle, "note", null, null, null);

        AppointmentResponseDto result = ctrl.createAppointment(request);
        assertNotNull(result);
        assertEquals("confirmed", result.status());
    }

    @Test
    void appointmentsController_getAppointment_returnsResponse() {
        AppointmentsController ctrl = new AppointmentsController(appointmentsService);
        when(appointmentsService.getAppointment("1", "branch-1", null)).thenReturn(
            new AppointmentResponseDto("1", "confirmed", "2026-04-15", "14:30", "Branch", List.of(), "Max M", "B-MS1234")
        );
        AppointmentResponseDto result = ctrl.getAppointment("1", "branch-1", null);
        assertNotNull(result);
        assertEquals("1", result.id());
    }

    @Test
    void appointmentsController_cancelAppointment_returnsCancelled() {
        AppointmentsController ctrl = new AppointmentsController(appointmentsService);
        when(appointmentsService.cancelAppointment("1", "branch-1", null)).thenReturn(
            new AppointmentResponseDto("1", "cancelled", "2026-04-15", "14:30", "Branch", List.of(), "Max M", "B-MS1234")
        );
        AppointmentResponseDto result = ctrl.cancelAppointment("1", "branch-1", null);
        assertEquals("cancelled", result.status());
    }

    // === SnapsController ===

    @Test
    void snapsController_getSnaps_returnsList() {
        SnapsController ctrl = new SnapsController(snapsService);
        SnapRequestDto request = new SnapRequestDto("b1", List.of("jt1"), "2026-04-15", "2026-04-20", "date-range", null, null, null, null, null);
        when(snapsService.getSnaps(any())).thenReturn(List.of(
            new SnapResponseDto("2026-04-15", "Di 15.04.", "Di", List.of())
        ));
        List<SnapResponseDto> result = ctrl.getSnaps(request);
        assertEquals(1, result.size());
    }

    @Test
    void snapsController_getSnaps_emptyResult() {
        SnapsController ctrl = new SnapsController(snapsService);
        SnapRequestDto request = new SnapRequestDto("b1", List.of("jt1"), "2026-04-15", "2026-04-20", "date-range", null, null, null, null, null);
        when(snapsService.getSnaps(any())).thenReturn(List.of());
        assertTrue(ctrl.getSnaps(request).isEmpty());
    }

    // === HealthController ===

    @Test
    void healthController_getHealth_healthy() {
        HealthController ctrl = new HealthController(healthService);
        when(healthService.checkHealth()).thenReturn(new HealthService.HealthStatus(
            "healthy", 100, "2026-04-16T12:00:00Z",
            new HealthService.HealthStatus.Dependencies(new HealthService.HealthStatus.SoftnetStatus("healthy", 50))
        ));
        HealthService.HealthStatus result = ctrl.getHealth();
        assertEquals("healthy", result.status());
        assertEquals(100, result.uptime());
        assertNotNull(result.dependencies());
        assertEquals("healthy", result.dependencies().softnet().status());
    }

    @Test
    void healthController_getHealth_degraded() {
        HealthController ctrl = new HealthController(healthService);
        when(healthService.checkHealth()).thenReturn(new HealthService.HealthStatus(
            "degraded", 200, "2026-04-16T12:00:00Z",
            new HealthService.HealthStatus.Dependencies(new HealthService.HealthStatus.SoftnetStatus("unhealthy", 5000))
        ));
        HealthService.HealthStatus result = ctrl.getHealth();
        assertEquals("degraded", result.status());
    }

    // === SessionController ===

    // === RentCategoriesController ===

    @Test
    void rentCategoriesController_getRentCategories_returnsList() {
        RentCategoriesController ctrl = new RentCategoriesController(rentCategoriesService);
        RentCategoryResponseDto dto = new RentCategoryResponseDto("rc-1", "grp-1", "Kleinwagen", "desc", null, null, null, List.of("VW"), List.of("branch-1"));
        when(rentCategoriesService.getRentCategories("branch-1")).thenReturn(List.of(dto));

        List<RentCategoryResponseDto> result = ctrl.getRentCategories("branch-1");
        assertEquals(1, result.size());
        assertEquals("rc-1", result.get(0).id());
        assertEquals("Kleinwagen", result.get(0).name());
    }

    @Test
    void sessionController_getToken_returnsTokenResult() {
        SessionController ctrl = new SessionController(sessionTokenService);
        when(sessionTokenService.generateToken()).thenReturn(new SessionTokenService.TokenResult("abc-token", "2026-04-16T13:00:00Z"));
        SessionTokenService.TokenResult result = ctrl.getToken();
        assertEquals("abc-token", result.token());
        assertEquals("2026-04-16T13:00:00Z", result.expiresAt());
    }
}
