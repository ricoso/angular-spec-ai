import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { WorkshopCalendarDatePickerComponent } from './workshop-calendar-date-picker.component';

describe('WorkshopCalendarDatePickerComponent', () => {
  let component: WorkshopCalendarDatePickerComponent;
  let fixture: ComponentFixture<WorkshopCalendarDatePickerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkshopCalendarDatePickerComponent]
    })
      .overrideComponent(WorkshopCalendarDatePickerComponent, {
        set: { template: '<div class="mocked">Mocked DatePicker</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(WorkshopCalendarDatePickerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have selectedDate input defaulting to null', () => {
    expect(component.selectedDate()).toBeNull();
  });

  it('should accept selectedDate input', () => {
    fixture.componentRef.setInput('selectedDate', '2026-03-02');
    fixture.detectChanges();

    expect(component.selectedDate()).toBe('2026-03-02');
  });

  it('should emit dateSelected with the parsed Date on a valid native change', () => {
    const spy = jest.fn();
    component.dateSelected.subscribe(spy);

    const event = { target: { value: '2026-08-15' } } as unknown as Event;
    const exposed = component as unknown as { onNativeDateChange: (e: Event) => void };
    exposed.onNativeDateChange(event);

    expect(spy).toHaveBeenCalledTimes(1);
    const emitted = spy.mock.calls[0][0] as Date;
    expect(emitted).toBeInstanceOf(Date);
    expect(emitted.getFullYear()).toBe(2026);
    expect(emitted.getMonth()).toBe(7); // August (0-based)
    expect(emitted.getDate()).toBe(15);
  });

  it('should not emit dateSelected on an empty value', () => {
    const spy = jest.fn();
    component.dateSelected.subscribe(spy);

    const event = { target: { value: '' } } as unknown as Event;
    const exposed = component as unknown as { onNativeDateChange: (e: Event) => void };
    exposed.onNativeDateChange(event);

    expect(spy).not.toHaveBeenCalled();
  });

  it('should expose minIso and maxIso bounds for the native date input', () => {
    const exposed = component as unknown as { minIso: string; maxIso: string };

    // Both are ISO date strings (YYYY-MM-DD).
    expect(exposed.minIso).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(exposed.maxIso).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    // minIso is today, maxIso is exactly one year later.
    const today = new Date();
    const expectedMin = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    expect(exposed.minIso).toBe(expectedMin);

    const maxYear = Number(exposed.maxIso.slice(0, 4));
    expect(maxYear).toBe(today.getFullYear() + 1);
  });
});
