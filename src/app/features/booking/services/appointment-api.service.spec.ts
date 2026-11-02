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

  it('should forward timePreference=AM in the POST body', async () => {
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'AM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    expect(req.request.body.timePreference).toBe('AM');
    req.flush(mockSnapResponse);
    await promise;
  });

  it('should forward timePreference=PM in the POST body', async () => {
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'PM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    expect(req.request.body.timePreference).toBe('PM');
    req.flush(mockSnapResponse);
    await promise;
  });

  it('should omit timePreference when not passed', async () => {
    const promise = service.getAppointments('branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    expect(req.request.body.timePreference).toBeUndefined();
    req.flush(mockSnapResponse);
    await promise;
  });

  it('should prefer afternoon slots when timePreference=PM', async () => {
    const responseWithMixedSlots = {
      data: [
        {
          date: '2026-04-20',
          displayDate: '20.04.2026',
          dayAbbreviation: 'Mo',
          slots: [
            { id: 'morning', time: '08:00', displayTime: '08:00 Uhr', available: true },
            { id: 'afternoon', time: '14:00', displayTime: '14:00 Uhr', available: true },
          ],
        },
      ],
    };
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'PM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(responseWithMixedSlots);

    const slots = await promise;
    expect(slots[0].id).toBe('afternoon');
    expect(slots[0].time).toBe('14:00');
  });

  it('should prefer morning slots when timePreference=AM', async () => {
    const responseWithMixedSlots = {
      data: [
        {
          date: '2026-04-20',
          displayDate: '20.04.2026',
          dayAbbreviation: 'Mo',
          slots: [
            { id: 'afternoon', time: '14:00', displayTime: '14:00 Uhr', available: true },
            { id: 'morning', time: '08:00', displayTime: '08:00 Uhr', available: true },
          ],
        },
      ],
    };
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'AM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(responseWithMixedSlots);

    const slots = await promise;
    expect(slots[0].id).toBe('morning');
    expect(slots[0].time).toBe('08:00');
  });

  it('should fall back to earliest slot when no PM slot exists (soft preference)', async () => {
    // Day 1: morning-only → falls back to morning slot
    // Day 2: has PM slot → uses it
    const mixed = {
      data: [
        {
          date: '2026-04-20',
          displayDate: '20.04.2026',
          dayAbbreviation: 'Mo',
          slots: [{ id: 'morning-only', time: '08:00', displayTime: '08:00 Uhr', available: true }],
        },
        {
          date: '2026-04-21',
          displayDate: '21.04.2026',
          dayAbbreviation: 'Di',
          slots: [{ id: 'pm-slot', time: '15:00', displayTime: '15:00 Uhr', available: true }],
        },
      ],
    };
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'PM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mixed);

    const slots = await promise;
    expect(slots).toHaveLength(2);
    expect(slots.find(s => s.date === '2026-04-20')?.id).toBe('morning-only');
    expect(slots.find(s => s.date === '2026-04-21')?.id).toBe('pm-slot');
  });

  it('should still return slots when NO day has PM (soft fallback to morning)', async () => {
    const morningOnly = {
      data: [
        {
          date: '2026-04-20',
          displayDate: '20.04.2026',
          dayAbbreviation: 'Mo',
          slots: [{ id: 'morning', time: '08:00', displayTime: '08:00 Uhr', available: true }],
        },
      ],
    };
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', 'PM');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(morningOnly);

    const slots = await promise;
    expect(slots).toHaveLength(1);
    expect(slots[0].id).toBe('morning');
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

  // REQ-035: Boxenstopp — Samstage sind fuer den Radwechsel reserviert, deshalb
  // bevorzugt die Schnellauswahl im tireChange-Flow die naechsten 3 Samstage.
  describe('tireChange Saturday preference (REQ-035)', () => {
    const day = (date: string, abbr: string, slotId: string): object => ({
      date,
      displayDate: date.split('-').reverse().join('.'),
      dayAbbreviation: abbr,
      slots: [{ id: slotId, time: '08:00', displayTime: '08:00 Uhr', available: true }],
    });

    it('should list the next 3 Saturdays first, then fill with earliest weekday', async () => {
      const response = { data: [
        day('2026-10-05', 'Mo', 'mo-1'),
        day('2026-10-06', 'Di', 'di-1'),
        day('2026-10-10', 'Sa', 'sa-1'),
        day('2026-10-12', 'Mo', 'mo-2'),
        day('2026-10-17', 'Sa', 'sa-2'),
        day('2026-10-24', 'Sa', 'sa-3'),
        day('2026-10-31', 'Sa', 'sa-4'),
      ] };
      const promise = service.getAppointments('branch-1', ['stored-tires'], 'volkswagen', 'tireChange');
      const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
      req.flush(response);

      const slots = await promise;
      expect(slots.map(s => s.id)).toEqual(['sa-1', 'sa-2', 'sa-3', 'mo-1']);
    });

    it('should fill with weekdays when fewer than 3 Saturdays have slots', async () => {
      const response = { data: [
        day('2026-10-05', 'Mo', 'mo-1'),
        day('2026-10-06', 'Di', 'di-1'),
        day('2026-10-07', 'Mi', 'mi-1'),
        day('2026-10-10', 'Sa', 'sa-1'),
        day('2026-10-12', 'Mo', 'mo-2'),
      ] };
      const promise = service.getAppointments('branch-1', ['stored-tires'], 'volkswagen', 'tireChange');
      const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
      req.flush(response);

      const slots = await promise;
      expect(slots.map(s => s.id)).toEqual(['sa-1', 'mo-1', 'di-1', 'mi-1']);
    });

    it('should fall back to earliest 4 days when no Saturday has slots', async () => {
      const response = { data: [
        day('2026-10-05', 'Mo', 'mo-1'),
        day('2026-10-06', 'Di', 'di-1'),
        day('2026-10-07', 'Mi', 'mi-1'),
        day('2026-10-08', 'Do', 'do-1'),
        day('2026-10-09', 'Fr', 'fr-1'),
      ] };
      const promise = service.getAppointments('branch-1', ['stored-tires'], 'volkswagen', 'tireChange');
      const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
      req.flush(response);

      const slots = await promise;
      expect(slots.map(s => s.id)).toEqual(['mo-1', 'di-1', 'mi-1', 'do-1']);
    });

    it('should NOT reorder days for the default variant (behaviour unchanged)', async () => {
      const response = { data: [
        day('2026-10-05', 'Mo', 'mo-1'),
        day('2026-10-06', 'Di', 'di-1'),
        day('2026-10-10', 'Sa', 'sa-1'),
        day('2026-10-12', 'Mo', 'mo-2'),
        day('2026-10-17', 'Sa', 'sa-2'),
      ] };
      const promise = service.getAppointments('branch-1', ['inspection'], 'volkswagen', 'default');
      const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
      req.flush(response);

      const slots = await promise;
      expect(slots.map(s => s.id)).toEqual(['mo-1', 'di-1', 'sa-1', 'mo-2']);
    });
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

  // REQ-039 Abschnitt 18
  it('should send replacementCar=true and map rentalCarIncluded when a rental car is requested (AC-M1)', async () => {
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default', undefined, undefined, true);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    expect(req.request.body.replacementCar).toBe(true);
    req.flush({
      data: [{
        date: '2026-04-10', displayDate: '10.04.2026', dayAbbreviation: 'Fr',
        slots: [{ id: 'slot-r', time: '08:00', displayTime: '08:00 Uhr', available: true, rentalCarIncluded: true }]
      }]
    });
    const slots = await promise;
    expect(slots[0].rentalCarIncluded).toBe(true);
  });

  it('should not send replacementCar without rental car — request unchanged (AC-M3)', async () => {
    const promise = service.getAppointments('branch-1', ['inspection'], 'audi', 'default');
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    expect(req.request.body.replacementCar).toBeUndefined();
    req.flush(mockSnapResponse);
    const slots = await promise;
    expect(slots.every(slot => slot.rentalCarIncluded === false)).toBe(true);
  });
});

