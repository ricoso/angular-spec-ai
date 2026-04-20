import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
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
  imports: [TranslatePipe, MatButtonModule, MatIconModule, WizardBreadcrumbComponent, NotesFormComponent, ServiceHintsComponent, NotesExtrasFormComponent],
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
  protected readonly initialNote = this.store.bookingNote;
  protected readonly initialExtras = this.store.notesExtras;
  protected readonly currentNote = signal<string | null>(this.store.bookingNote());
  protected readonly currentExtras = signal<NotesExtras>(
    this.store.notesExtras() ?? { ...DEFAULT_NOTES_EXTRAS }
  );

  protected onNoteChanged(note: string | null): void {
    this.currentNote.set(note);
  }

  protected onExtrasChanged(extras: NotesExtras): void {
    this.currentExtras.set(extras);
  }

  protected onContinue(): void {
    const note = this.currentNote();
    const sanitizedNote = note && note.trim().length > 0 ? note.trim() : null;
    this.store.setBookingNote(sanitizedNote);
    this.store.setNotesExtras(this.currentExtras());
    console.debug('[NotesContainer] Continue clicked, note:', sanitizedNote, 'extras:', this.currentExtras());
    void this.router.navigate(['/home/appointment']);
  }

  protected onBack(): void {
    this.store.setBookingNote(null);
    this.store.clearNotesExtras();
    void this.router.navigate(['/home/services']);
  }
}
