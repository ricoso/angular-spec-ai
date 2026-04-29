import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AppErrorStore } from '@app/core/error-handling/app-error.store';
import type { CriticalError } from '@app/core/error-handling/models/critical-error.model';
import { AppointmentApiService } from '@app/features/booking/services/appointment-api.service';
import { BookingApiService } from '@app/features/booking/services/booking-api.service';
import { RentCategoryApiService } from '@app/features/booking/services/rent-category-api.service';
import { BookingStore } from '@app/features/booking/stores/booking.store';

import { ErrorFallbackContainerComponent } from './error-fallback-container.component';

describe('ErrorFallbackContainerComponent', () => {
  let component: ErrorFallbackContainerComponent;
  let fixture: ComponentFixture<ErrorFallbackContainerComponent>;
  let errorStore: InstanceType<typeof AppErrorStore>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorFallbackContainerComponent],
      providers: [
        provideRouter([]),
        BookingStore,
        AppErrorStore,
        { provide: BookingApiService, useValue: { getBranches: jest.fn().mockResolvedValue([]), getBrands: jest.fn().mockResolvedValue([]), getLocations: jest.fn().mockResolvedValue([]), getServices: jest.fn().mockResolvedValue([]) } },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1' }), getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1' }) } },
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } }
      ]
    })
      .overrideComponent(ErrorFallbackContainerComponent, {
        set: { template: '<div class="mocked">Mocked</div>' }
      })
      .compileComponents();

    errorStore = TestBed.inject(AppErrorStore);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(ErrorFallbackContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose criticalError signal from store', () => {
    const exposed = component as unknown as { criticalError: () => CriticalError | null };
    expect(exposed.criticalError()).toBeNull();

    const err: CriticalError = { code: 'NETWORK', message: 'm', requestId: null, timestamp: 't' };
    errorStore.setCriticalError(err);
    expect(exposed.criticalError()).toEqual(err);
  });

  it('should clear error and navigate on retry', () => {
    errorStore.setCriticalError({ code: 'NETWORK', message: 'm', requestId: null, timestamp: 't' });
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();

    expect(errorStore.criticalError()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/home/location']);
  });

  it('should compute isOffline from navigator.onLine', () => {
    const exposed = component as unknown as { isOffline: () => boolean };
    const original = navigator.onLine;
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false });
    expect(exposed.isOffline()).toBe(true);
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => original });
  });
});
