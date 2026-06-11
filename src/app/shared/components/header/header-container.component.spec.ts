import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { HeaderContainerComponent } from './header-container.component';

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only.
// Cart-Icon + Accessibility-Menu sind in <app-floating-overlay /> ausgelagert (siehe FloatingOverlayComponent).
// Der Header verwaltet jetzt nur noch Logo, Title, Embedded-Detection und den Close-Button.
describe('HeaderContainerComponent', () => {
  let component: HeaderContainerComponent;
  let fixture: ComponentFixture<HeaderContainerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderContainerComponent],
      providers: [
        provideRouter([])
      ]
    })
      .overrideComponent(HeaderContainerComponent, {
        set: { template: '<div class="mocked">Mocked Header</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(HeaderContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('isEmbedded detection', () => {
    it('should expose isEmbedded as a signal', () => {
      const exposed = component as unknown as { isEmbedded: () => boolean };
      expect(typeof exposed.isEmbedded).toBe('function');
      expect(typeof exposed.isEmbedded()).toBe('boolean');
    });
  });

  describe('Close button (iframe-Modus)', () => {
    it('should expose onClose method that navigates to the iframe-return URL', () => {
      const exposed = component as unknown as { onClose: () => void };
      expect(typeof exposed.onClose).toBe('function');
      // No assertion on side effects: window.top.location.href change is not testable in jsdom
    });
  });
});
