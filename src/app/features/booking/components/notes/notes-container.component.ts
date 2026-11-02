import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { NotesExtras } from '../../models/notes-extras.model';
import { DEFAULT_NOTES_EXTRAS } from '../../models/notes-extras.model';
import { BookingStore } from '../../stores/booking.store';

import { NotesExtrasFormComponent } from './notes-extras-form.component';
import { NotesFormComponent } from './notes-form.component';
import { ServiceHintsComponent } from './service-hints.component';

@Component({
  selector: 'app-notes-container',
  standalone: true,
  imports: [TranslatePipe, WizardBreadcrumbComponent, NotesFormComponent, ServiceHintsComponent, NotesExtrasFormComponent],
  templateUrl: './notes-container.component.html',
  styleUrl: './notes-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotesContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 3;
  protected readonly selectedServices = this.store.selectedServices;
  protected readonly jobTypes = this.store.jobTypes;
  protected readonly operations = this.store.operations;
  protected readonly initialNote = this.store.bookingNote;
  protected readonly initialExtras = this.store.notesExtras;
  // REQ-039 BR-M8: only the categories valid for the selected brand (the switch itself is hidden
  // for brands without a matching category via hasRentalCarOffer).
  protected readonly rentCategories = this.store.rentCategoriesForBrand;
  protected readonly initialRentCategoryId = this.store.selectedRentCategoryId;
  protected readonly initialRentalCarRequested = this.store.rentalCarRequested;
  protected readonly rentalCarOffered = this.store.hasRentalCarOffer;
  protected readonly rentalCarUnavailable = this.store.hasNoRentalCarOffer;
  protected readonly currentNote = signal<string | null>(this.store.bookingNote());
  protected readonly currentExtras = signal<NotesExtras>(
    this.store.notesExtras() ?? { ...DEFAULT_NOTES_EXTRAS }
  );
  protected readonly currentRentCategoryId = signal<string | null>(this.store.selectedRentCategoryId());
  protected readonly currentRentalCarRequested = signal<boolean>(this.store.rentalCarRequested());

  protected onNoteChanged(note: string | null): void {
    this.currentNote.set(note);
  }

  protected onExtrasChanged(extras: NotesExtras): void {
    this.currentExtras.set(extras);
  }

  protected onRentCategoryChanged(id: string | null): void {
    this.currentRentCategoryId.set(id);
  }

  protected onRentalCarChanged(requested: boolean): void {
    this.currentRentalCarRequested.set(requested);
  }

  protected onContinue(): void {
    const note = this.currentNote();
    const sanitizedNote = note && note.trim().length > 0 ? note.trim() : null;
    this.store.setBookingNote(sanitizedNote);
    this.store.setNotesExtras(this.currentExtras());
    // REQ-039 BR-M8: without rent categories there is no switch — never request a rental car then.
    const rentalCarRequested = this.rentalCarOffered() && this.currentRentalCarRequested();
    this.store.setRentalCarRequested(rentalCarRequested);
    this.store.setSelectedRentCategoryId(rentalCarRequested ? this.currentRentCategoryId() : null);
    void this.router.navigate(['/home/appointment']);
  }

  protected onBack(): void {
    this.store.setBookingNote(null);
    this.store.clearNotesExtras();
    this.store.setSelectedRentCategoryId(null);
    this.store.setRentalCarRequested(false);
    // REQ-041: the service step is skipped in recall mode — going back there would reload the
    // default-group catalogue and overwrite the recall operation. Return to brand instead.
    void this.router.navigate([this.store.isRecallMode() ? '/home/brand' : '/home/services']);
  }
}
