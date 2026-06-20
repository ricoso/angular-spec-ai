import type { WritableSignal } from '@angular/core';
import { signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AppErrorStore } from '@app/core/error-handling/app-error.store';

import { AppComponent } from './app.component';

describe('AppComponent', () => {
  let criticalError: WritableSignal<boolean>;
  let navigate: jest.Mock;
  let navigateByUrl: jest.Mock;
  let routerUrl: string;

  beforeEach(() => {
    criticalError = signal(false);
    navigate = jest.fn().mockResolvedValue(true);
    navigateByUrl = jest.fn().mockResolvedValue(true);
    routerUrl = '/home/location';

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: AppErrorStore, useValue: { hasCriticalError: criticalError } },
        {
          provide: Router,
          useValue: {
            get url(): string {
              return routerUrl;
            },
            navigate,
            navigateByUrl
          }
        }
      ]
    }).overrideComponent(AppComponent, {
      set: { template: '<div class="mocked">Mocked AppComponent</div>' }
    });
  });

  afterEach(() => {
    window.location.hash = '';
    jest.useRealTimers();
  });

  function create(): ComponentFixture<AppComponent> {
    return TestBed.createComponent(AppComponent);
  }

  describe('critical-error redirect effect', () => {
    it('navigates to /app-error when a critical error occurs and we are elsewhere', () => {
      routerUrl = '/home/location';
      criticalError.set(true);

      const fixture = create();
      fixture.detectChanges();

      expect(navigate).toHaveBeenCalledWith(['/app-error']);
    });

    it('does not navigate when there is no critical error', () => {
      criticalError.set(false);

      const fixture = create();
      fixture.detectChanges();

      expect(navigate).not.toHaveBeenCalled();
    });

    it('does not navigate again when already on /app-error', () => {
      routerUrl = '/app-error';
      criticalError.set(true);

      const fixture = create();
      fixture.detectChanges();

      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('ngOnInit reload redirect', () => {
    it('redirects to /home/location from an unrelated path', () => {
      jest.useFakeTimers();
      window.location.hash = '';

      const fixture = create();
      fixture.componentInstance.ngOnInit();
      jest.runAllTimers();

      expect(navigateByUrl).toHaveBeenCalledWith('/home/location', { replaceUrl: true });
    });

    it('does not redirect when already on /home/location', () => {
      jest.useFakeTimers();
      window.location.hash = '#/home/location';

      const fixture = create();
      fixture.componentInstance.ngOnInit();
      jest.runAllTimers();

      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('does not redirect away from the /app-error route', () => {
      jest.useFakeTimers();
      window.location.hash = '#/app-error';

      const fixture = create();
      fixture.componentInstance.ngOnInit();
      jest.runAllTimers();

      expect(navigateByUrl).not.toHaveBeenCalled();
    });
  });
});
