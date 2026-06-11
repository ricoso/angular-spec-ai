import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { i18nKeys } from '@core/i18n';
import { AppointmentApiService } from '@features/booking/services/appointment-api.service';
import { BookingApiService } from '@features/booking/services/booking-api.service';
import { RentCategoryApiService } from '@features/booking/services/rent-category-api.service';
import { BookingStore } from '@features/booking/stores/booking.store';

import { WizardBreadcrumbComponent } from './wizard-breadcrumb.component';
import type { WizardStep } from './wizard-breadcrumb.model';
import { WIZARD_STEPS } from './wizard-steps.config';

// Logic-only Tests fuer Wizard-Breadcrumb. UI ueber E2E.
describe('WizardBreadcrumbComponent', () => {
  let component: WizardBreadcrumbComponent;
  let fixture: ComponentFixture<WizardBreadcrumbComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn().mockResolvedValue(true) } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [WizardBreadcrumbComponent],
      providers: [
        BookingStore,
        { provide: Router, useValue: router },
        {
          provide: BookingApiService,
          useValue: {
            getBranches: jest.fn().mockResolvedValue([]),
            getBrands: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([]),
            getCategoriesByBranch: jest.fn().mockResolvedValue([]),
            getOperationsByBranch: jest.fn().mockResolvedValue([])
          }
        },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]) } },
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } }
      ]
    })
      .overrideComponent(WizardBreadcrumbComponent, {
        set: { template: '<nav class="mocked"></nav>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(WizardBreadcrumbComponent);
    component = fixture.componentInstance;
    store = TestBed.inject(BookingStore);
    fixture.componentRef.setInput('steps', WIZARD_STEPS);
    fixture.componentRef.setInput('activeIndex', 0);
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('stepsWithStatus computed', () => {
    it('should mark first step active when activeIndex=0', () => {
      const exposed = component as unknown as { stepsWithStatus: () => { status: string; isLast: boolean; originalIndex: number }[] };
      const result = exposed.stepsWithStatus();
      expect(result[0].status).toBe('active');
      expect(result[1].status).toBe('future');
    });

    it('should mark earlier steps as done', () => {
      fixture.componentRef.setInput('activeIndex', 3);
      fixture.detectChanges();
      const exposed = component as unknown as { stepsWithStatus: () => { status: string }[] };
      const result = exposed.stepsWithStatus();
      expect(result[0].status).toBe('done');
      expect(result[1].status).toBe('done');
      expect(result[2].status).toBe('done');
      expect(result[3].status).toBe('active');
      expect(result[4].status).toBe('future');
    });

    it('should set isLast=true for the final step', () => {
      const exposed = component as unknown as { stepsWithStatus: () => { isLast: boolean }[] };
      const result = exposed.stepsWithStatus();
      expect(result[result.length - 1].isLast).toBe(true);
      expect(result[0].isLast).toBe(false);
    });

    it('should preserve originalIndex when not deep-linked', () => {
      const exposed = component as unknown as { stepsWithStatus: () => { originalIndex: number }[] };
      const result = exposed.stepsWithStatus();
      result.forEach((s, i) => { expect(s.originalIndex).toBe(i); });
    });
  });

  describe('onStepClick', () => {
    it('should not navigate when clicking the active or a future step', () => {
      fixture.componentRef.setInput('activeIndex', 2);
      fixture.detectChanges();
      const exposed = component as unknown as { onStepClick: (e: Event, idx: number) => void };
      const event = { preventDefault: jest.fn() } as unknown as Event;
      exposed.onStepClick(event, 2);
      expect(router.navigate).not.toHaveBeenCalled();
      exposed.onStepClick(event, 4);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should navigate to a previous step and reset store', () => {
      fixture.componentRef.setInput('activeIndex', 3);
      fixture.detectChanges();

      const resetSpy = jest.spyOn(store, 'resetFromStep');
      const exposed = component as unknown as { onStepClick: (e: Event, idx: number) => void };
      const event = { preventDefault: jest.fn() } as unknown as Event;
      exposed.onStepClick(event, 1);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(resetSpy).toHaveBeenCalledWith(1);
      expect(router.navigate).toHaveBeenCalledWith([WIZARD_STEPS[1].route]);
    });
  });

  describe('WIZARD_STEPS config', () => {
    it('should expose 7 steps with Bootstrap-Icon classes', () => {
      expect(WIZARD_STEPS).toHaveLength(7);
      WIZARD_STEPS.forEach((step: WizardStep) => {
        expect(step.icon).toMatch(/^bi-/);
      });
    });

    it('should reference valid i18n keys', () => {
      expect(WIZARD_STEPS[0].label).toBe(i18nKeys.booking.wizard.location);
      expect(WIZARD_STEPS[6].label).toBe(i18nKeys.booking.wizard.overview);
    });
  });
});
