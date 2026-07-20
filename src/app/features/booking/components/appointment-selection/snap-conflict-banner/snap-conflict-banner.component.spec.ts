import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { SNAP_CONFLICT_BANNER_TIMEOUT_MS, SnapConflictBannerComponent } from './snap-conflict-banner.component';

describe('SnapConflictBannerComponent', () => {
  let component: SnapConflictBannerComponent;
  let fixture: ComponentFixture<SnapConflictBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SnapConflictBannerComponent]
    })
      .overrideComponent(SnapConflictBannerComponent, {
        set: { template: '<div class="mocked">Mocked SnapConflictBanner</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(SnapConflictBannerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit closed when onClose() is called', () => {
    const closedSpy = jest.fn();
    component.closed.subscribe(closedSpy);

    const exposed = component as unknown as { onClose: () => void };
    exposed.onClose();

    expect(closedSpy).toHaveBeenCalledTimes(1);
  });

  it('should emit closed once per close interaction', () => {
    const closedSpy = jest.fn();
    component.closed.subscribe(closedSpy);

    const exposed = component as unknown as { onClose: () => void };
    exposed.onClose();
    exposed.onClose();

    expect(closedSpy).toHaveBeenCalledTimes(2);
  });

  it('should expose the snapConflict i18n key alias', () => {
    const exposed = component as unknown as { snapConflict: { title: string } };
    expect(exposed.snapConflict.title).toBe('booking.appointment.snapConflict.title');
  });

  it('should define a 30 second auto-dismiss timeout (BR-3)', () => {
    expect(SNAP_CONFLICT_BANNER_TIMEOUT_MS).toBe(30_000);
  });
});
