import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { AppointmentApiService } from './appointment-api.service';

const mockSnapResponse = {
  data: [
    {
      date: '2026-04-10',
      displayDate: '10.04.2026',
      dayAbbreviation: 'Fr',
      slots: [
        { id: 'slot-1', time: '08:00', displayTime: '08:00 Uhr', available: true },
        { id: 'slot-2', time: '09:00', displayTime: '09:00 Uhr', available: false },
      ],
    },
    {
      date: '2026-04-11',
      displayDate: '11.04.2026',
      dayAbbreviation: 'Sa',
      slots: [
        { id: 'slot-3', time: '10:00', displayTime: '10:00 Uhr', available: true },
      ],
    },
    {
      date: '2026-04-13',
      displayDate: '13.04.2026',
      dayAbbreviation: 'Mo',
      slots: [
        { id: 'slot-4', time: '07:00', displayTime: '07:00 Uhr', available: true },
      ],
    },
    {
      date: '2026-04-14',
      displayDate: '14.04.2026',
      dayAbbreviation: 'Di',
      slots: [
        { id: 'slot-5', time: '14:00', displayTime: '14:00 Uhr', available: true },
      ],
    },
    {
      date: '2026-04-15',
      displayDate: '15.04.2026',
      dayAbbreviation: 'Mi',
      slots: [
        { id: 'slot-6', time: '16:00', displayTime: '16:00 Uhr', available: true },
      ],
    },
  ],
};

describe('AppointmentApiService', () => {
  let service: AppointmentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AppointmentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return at most 4 appointment slots', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    expect(slots.length).toBeLessThanOrEqual(4);
    expect(slots.length).toBeGreaterThanOrEqual(1);
  });

  it('should return slots with all required fields', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    for (const slot of slots) {
      expect(slot.id).toBeDefined();
      expect(slot.date).toBeDefined();
      expect(slot.displayDate).toBeDefined();
      expect(slot.dayAbbreviation).toBeDefined();
      expect(slot.time).toBeDefined();
      expect(slot.displayTime).toBeDefined();
    }
  });

  it('should return valid day abbreviations (Mo-Sa)', async () => {
    const validAbbreviations = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    for (const slot of slots) {
      expect(validAbbreviations).toContain(slot.dayAbbreviation);
    }
  });

  it('should return unique slot IDs', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    const ids = slots.map(s => s.id);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(ids.length);
  });

  it('should format displayDate as DD.MM.YYYY', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    for (const slot of slots) {
      expect(slot.displayDate).toMatch(/^\d{2}\.\d{2}\.\d{4}$/);
    }
  });

  it('should format displayTime with "Uhr" suffix', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    for (const slot of slots) {
      expect(slot.displayTime).toMatch(/^\d{2}:\d{2} Uhr$/);
    }
  });

  it('should pick first available slot from each day', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const slots = await promise;
    // First day has slot-1 (available) and slot-2 (not available)
    expect(slots[0].id).toBe('slot-1');
    expect(slots[0].time).toBe('08:00');
  });

  it('should send POST request with correct body', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);

    expect(req.request.method).toBe('POST');
    expect(req.request.body.branchId).toBe('branch-1');
    expect(req.request.body.mode).toBe('next-available');

    req.flush(mockSnapResponse);
    await promise;
  });

  it('should create appointment via POST with new DTO format', async () => {
    const request = {
      snapId: 'snap-123',
      branchId: 'branch-1',
      variant: 'default' as const,
      customer: {
        email: 'test@test.de',
        firstName: 'Max',
        lastName: 'Mustermann',
        mobile: '+491234567890',
        address: 'Teststraße 1',
        zipCode: '80331',
        city: 'München',
      },
      vehicle: {
        licensePlate: 'M-TEST-123',
        mileage: 50000,
      },
    };
    const mockAppointment = { id: 'apt-1', status: 'confirmed', date: '2026-04-10', time: '08:00', branchName: 'Berlin', services: [], customerName: 'Max Mustermann', licensePlate: 'M-TEST-123' };

    const promise = service.createAppointment(request);
    const req = httpMock.expectOne(`${environment.apiUrl}/appointments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.snapId).toBe('snap-123');
    expect(req.request.body.customer.email).toBe('test@test.de');
    expect(req.request.body.vehicle.licensePlate).toBe('M-TEST-123');
    req.flush({ data: mockAppointment });

    const result = await promise;
    expect(result.id).toBe('apt-1');
    expect(result.status).toBe('confirmed');
  });

  it('should get appointment by ID via GET and unwrap response', async () => {
    const mockAppointment = { id: 'apt-1', status: 'confirmed', date: '2026-04-10', time: '08:00', branchName: 'Berlin', services: [], customerName: '', licensePlate: '' };

    const promise = service.getAppointment('apt-1', 'branch-1');
    const req = httpMock.expectOne(r => r.url === `${environment.apiUrl}/appointments/apt-1`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('branchId')).toBe('branch-1');
    req.flush({ data: mockAppointment });

    const result = await promise;
    expect(result.id).toBe('apt-1');
  });
});
