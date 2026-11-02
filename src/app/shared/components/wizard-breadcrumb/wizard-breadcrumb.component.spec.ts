import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { i18nKeys, TranslateService } from '@core/i18n';
import { AppointmentApiService } from '@features/booking/services/appointment-api.service';
import { BookingApiService } from '@features/booking/services/booking-api.service';
import { RentCategoryApiService } from '@features/booking/services/rent-category-api.service';
import { BookingStore } from '@features/booking/stores/booking.store';

import { WizardBreadcrumbComponent } from './wizard-breadcrumb.component';
import type { WizardStep } from './wizard-breadcrumb.model';
import { WIZARD_STEPS } from './wizard-steps.config';

describe('WizardBreadcrumbComponent', () => {
  let component: WizardBreadcrumbComponent;
  let fixture: ComponentFixture<WizardBreadcrumbComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;
  let hostElement: HTMLElement;
  let translate: TranslateService;

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
    }).compileComponents();

    translate = TestBed.inject(TranslateService);
    translate.use('de');
    fixture = TestBed.createComponent(WizardBreadcrumbComponent);
    component = fixture.componentInstance;
    hostElement = fixture.nativeElement as HTMLElement;
    store = TestBed.inject(BookingStore);
    fixture.componentRef.setInput('steps', WIZARD_STEPS);
    fixture.componentRef.setInput('activeIndex', 0);
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem('app-language');
    document.documentElement.removeAttribute('lang');
    document.documentElement.removeAttribute('dir');
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

  describe('shared responsive navigation', () => {
    it('renders one control per step, including the location in the standard flow', () => {
      expect(hostElement.querySelectorAll('.wizard__steps')).toHaveLength(1);
      expect(hostElement.querySelectorAll('.wizard__step-button')).toHaveLength(7);
      expect(hostElement.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
      expect(hostElement.querySelector('.wizard__current-title')?.textContent.trim()).toBe('Standort');
      expect(hostElement.querySelector('.wizard')?.getAttribute('aria-label')).toBe('Buchungsfortschritt');
    });

    it.each([
      [1, 'Marke'],
      [2, 'Service'],
      [3, 'Hinweise'],
      [4, 'Termin'],
      [5, 'Kontakt- & Fahrzeugdaten'],
      [6, 'Übersicht']
    ])('keeps the title, status and six visible steps synchronized at index %i', (activeIndex, title) => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', activeIndex);
      fixture.detectChanges();

      expect(hostElement.querySelectorAll('.wizard__step-button')).toHaveLength(6);
      expect(hostElement.querySelectorAll('.wizard__step--done')).toHaveLength(activeIndex - 1);
      expect(hostElement.querySelectorAll('.wizard__step--future')).toHaveLength(6 - activeIndex);
      expect(hostElement.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
      expect(hostElement.querySelector('[aria-current="step"]')?.getAttribute('aria-label')).toBe(title);
      expect(hostElement.querySelector('.wizard__current-title')?.textContent.trim()).toBe(title);
    });

    it('navigates through a completed icon with the original unfiltered index', () => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', 4);
      fixture.detectChanges();
      const resetSpy = jest.spyOn(store, 'resetFromStep');
      const brandIcon = hostElement.querySelector<HTMLElement>('.wizard__marker');

      brandIcon?.click();

      expect(resetSpy).toHaveBeenCalledTimes(1);
      expect(resetSpy).toHaveBeenCalledWith(1);
      expect(router.navigate).toHaveBeenCalledWith(['/home/brand']);
    });

    it('makes the entire completed step control clickable, not only its label', () => {
      fixture.componentRef.setInput('activeIndex', 4);
      fixture.detectChanges();
      const resetSpy = jest.spyOn(store, 'resetFromStep');
      const serviceButton = hostElement.querySelector<HTMLButtonElement>('[aria-label="Service"]');

      serviceButton?.click();

      expect(resetSpy).toHaveBeenCalledWith(2);
      expect(router.navigate).toHaveBeenCalledWith(['/home/services']);
    });

    it('keeps active and future point clicks as no-ops without resetting booking data', () => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', 3);
      fixture.detectChanges();
      const resetSpy = jest.spyOn(store, 'resetFromStep');
      const unavailableButtons = hostElement.querySelectorAll<HTMLButtonElement>('[aria-disabled="true"]');

      expect(unavailableButtons).toHaveLength(4);
      unavailableButtons.forEach(button => {
        expect(button.disabled).toBe(false);
        expect(button.tabIndex).toBe(0);
        button.click();
      });

      expect(resetSpy).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(hostElement.querySelector('.wizard__current-title')?.textContent.trim()).toBe('Hinweise');
    });

    it('provides an accessible name and textual status independently of visible labels', () => {
      fixture.componentRef.setInput('activeIndex', 2);
      fixture.detectChanges();

      const buttons = hostElement.querySelectorAll<HTMLButtonElement>('.wizard__step-button');
      buttons.forEach(button => {
        expect(button.getAttribute('aria-label')).toBeTruthy();
        const statusId = button.getAttribute('aria-describedby');
        expect(hostElement.querySelector(`#${statusId}`)?.textContent.trim()).toBeTruthy();
      });

      expect(hostElement.querySelector('#wizard-step-status-1')?.textContent.trim()).toBe('Abgeschlossen');
      expect(hostElement.querySelector('#wizard-step-status-2')?.textContent.trim()).toBe('Aktueller Schritt');
      expect(hostElement.querySelector('#wizard-step-status-3')?.textContent.trim()).toBe('Noch nicht verfügbar');
    });

    it('translates the live title, navigation and accessible labels together', () => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', 5);
      fixture.detectChanges();

      translate.use('en');
      fixture.detectChanges();

      expect(hostElement.querySelector('.wizard')?.getAttribute('aria-label')).toBe('Booking progress');
      expect(hostElement.querySelector('.wizard__current-title')?.textContent.trim()).toBe('Contact & vehicle details');
      expect(hostElement.querySelector('[aria-current="step"]')?.getAttribute('aria-label')).toBe('Contact & vehicle details');
      expect(hostElement.querySelector('#wizard-step-status-5')?.textContent.trim()).toBe('Current step');
    });

    it('updates the current title when navigating backward without retaining a second state', () => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', 6);
      fixture.detectChanges();
      fixture.componentRef.setInput('activeIndex', 2);
      fixture.detectChanges();

      expect(hostElement.querySelector('.wizard__current-title')?.textContent.trim()).toBe('Service');
      expect(hostElement.querySelectorAll('.wizard__step--done')).toHaveLength(1);
      expect(hostElement.querySelectorAll('.wizard__step--future')).toHaveLength(4);
    });

    it('does not navigate to the hidden location or an invalid step index', () => {
      store.setDeepLinked(true);
      fixture.componentRef.setInput('activeIndex', 6);
      fixture.detectChanges();
      const resetSpy = jest.spyOn(store, 'resetFromStep');
      const exposed = component as unknown as { onStepClick: (event: Event, index: number) => void };

      for (const index of [-1, 0, 7]) {
        exposed.onStepClick(new MouseEvent('click'), index);
      }

      expect(resetSpy).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });
});
