import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { PriceTileComponent } from './price-tile.component';

describe('PriceTileComponent', () => {
  let component: PriceTileComponent;
  let fixture: ComponentFixture<PriceTileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PriceTileComponent]
    })
      .overrideComponent(PriceTileComponent, {
        set: { template: '<div class="mocked">Mocked PriceTile</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(PriceTileComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('mobilityOption', 'none');
    fixture.componentRef.setInput('appointmentPreference', 'anytime');
    fixture.componentRef.setInput('callbackOption', 'none');
    fixture.componentRef.setInput('bookingNote', '');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have mobility option input', () => {
    expect(component.mobilityOption()).toBe('none');
  });

  it('should expose i18n keys', () => {
    const exposed = component as unknown as { bookingOverview: Record<string, unknown> };
    expect(exposed.bookingOverview).toBeDefined();
  });
});
