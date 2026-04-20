import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { WorkshopCalendarApiService } from './workshop-calendar-api.service';

const mockSnapResponse = {
  data: [
    {
      date: '2026-03-02',
      displayDate: '02.03.2026',
      dayAbbreviation: 'Mo',
      slots: [
        { id: 's1', time: '07:00', displayTime: '07:00 Uhr', available: true },
        { id: 's2', time: '08:00', displayTime: '08:00 Uhr', available: true },
        { id: 's3', time: '09:00', displayTime: '09:00 Uhr', available: true },
        { id: 's4', time: '10:00', displayTime: '10:00 Uhr', available: true },
        { id: 's5', time: '11:00', displayTime: '11:00 Uhr', available: true },
        { id: 's6', time: '12:00', displayTime: '12:00 Uhr', available: false },
        { id: 's7', time: '13:00', displayTime: '13:00 Uhr', available: true },
        { id: 's8', time: '14:00', displayTime: '14:00 Uhr', available: true },
        { id: 's9', time: '15:00', displayTime: '15:00 Uhr', available: true },
        { id: 's10', time: '16:00', displayTime: '16:00 Uhr', available: true },
        { id: 's11', time: '17:00', displayTime: '17:00 Uhr', available: true },
      ],
    },
    {
      date: '2026-03-03',
      displayDate: '03.03.2026',
      dayAbbreviation: 'Di',
      slots: [
        { id: 's12', time: '07:00', displayTime: '07:00 Uhr', available: true },
        { id: 's13', time: '08:00', displayTime: '08:00 Uhr', available: true },
        { id: 's14', time: '09:00', displayTime: '09:00 Uhr', available: true },
      ],
    },
    {
      date: '2026-03-04',
      displayDate: '04.03.2026',
      dayAbbreviation: 'Mi',
      slots: [
        { id: 's15', time: '07:00', displayTime: '07:00 Uhr', available: true },
        { id: 's16', time: '08:00', displayTime: '08:00 Uhr', available: true },
      ],
    },
  ],
};

describe('WorkshopCalendarApiService', () => {
  let service: WorkshopCalendarApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WorkshopCalendarApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return exactly 3 workshop calendar days', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    expect(days).toHaveLength(3);
  });

  it('should return days with all required fields', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    for (const day of days) {
      expect(day.date).toBeDefined();
      expect(day.displayDate).toBeDefined();
      expect(day.dayAbbreviation).toBeDefined();
      expect(day.displayHeading).toBeDefined();
      expect(day.slots).toBeDefined();
    }
  });

  it('should filter out unavailable slots', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    // First day has 11 slots, 1 unavailable → 10 available
    expect(days[0].slots).toHaveLength(10);
  });

  it('should return valid day abbreviations (Mo-Sa)', async () => {
    const validAbbreviations = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    for (const day of days) {
      expect(validAbbreviations).toContain(day.dayAbbreviation);
    }
  });

  it('should format displayDate as DD.MM.YYYY', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    for (const day of days) {
      expect(day.displayDate).toMatch(/^\d{2}\.\d{2}\.\d{4}$/);
    }
  });

  it('should format displayHeading as abbreviation + date', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    for (const day of days) {
      expect(day.displayHeading).toContain(day.dayAbbreviation);
      expect(day.displayHeading).toContain(day.displayDate);
    }
  });

  it('should format slot displayTime with "Uhr" suffix', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    for (const day of days) {
      for (const slot of day.slots) {
        expect(slot.displayTime).toMatch(/^\d{2}:\d{2} Uhr$/);
      }
    }
  });

  it('should return unique slot IDs', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);
    req.flush(mockSnapResponse);

    const days = await promise;
    const allIds = days.flatMap(d => d.slots.map(s => s.id));
    const uniqueIds = new Set(allIds);
    expect(uniqueIds.size).toBe(allIds.length);
  });

  it('should send POST request with date-range mode', async () => {
    const promise = service.getWorkshopCalendarDays('2026-03-02', 'branch-1', ['inspection']);
    const req = httpMock.expectOne(`${environment.apiUrl}/snaps`);

    expect(req.request.method).toBe('POST');
    expect(req.request.body.mode).toBe('date-range');
    expect(req.request.body.fromDate).toBe('2026-03-02');

    req.flush(mockSnapResponse);
    await promise;
  });
});
