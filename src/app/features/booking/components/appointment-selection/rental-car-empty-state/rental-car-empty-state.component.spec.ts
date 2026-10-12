import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { RentalCarEmptyStateComponent } from './rental-car-empty-state.component';

describe('RentalCarEmptyStateComponent (REQ-039 E-1/E-2)', () => {
  let component: RentalCarEmptyStateComponent;
  let fixture: ComponentFixture<RentalCarEmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RentalCarEmptyStateComponent] })
      .overrideComponent(RentalCarEmptyStateComponent, {
        set: { template: '<div class="mocked">Mocked Rental Empty State</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(RentalCarEmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should not offer later appointments by default', () => {
    expect(component.showLaterAvailable()).toBe(false);
  });

  it('should emit showLater', () => {
    const spy = jest.fn();
    component.showLater.subscribe(spy);
    (component as unknown as { onShowLater: () => void }).onShowLater();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('should emit continueWithout', () => {
    const spy = jest.fn();
    component.continueWithout.subscribe(spy);
    (component as unknown as { onContinueWithout: () => void }).onContinueWithout();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('should expose the rental car texts', () => {
    const exposed = component as unknown as { rentalCar: { noneAvailable: string; continueWithout: string } };
    expect(exposed.rentalCar.noneAvailable).toBe('booking.appointment.rentalCar.noneAvailable');
    expect(exposed.rentalCar.continueWithout).toBe('booking.appointment.rentalCar.continueWithout');
  });
});
