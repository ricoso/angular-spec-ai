import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  type OnInit,
  output,
  signal
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { type Subscription } from 'rxjs';

const MAX_NOTE_LENGTH = 1000;

@Component({
  selector: 'app-notes-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './notes-form.component.html',
  styleUrl: './notes-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotesFormComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private subscription: Subscription | null = null;

  public readonly initialNote = input<string | null>(null);
  public readonly noteChanged = output<string | null>();

  protected readonly booking = i18nKeys.booking;
  protected readonly maxLength = MAX_NOTE_LENGTH;
  protected readonly noteControl = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.maxLength(MAX_NOTE_LENGTH)]
  });
  protected readonly currentLength = signal(0);

  constructor() {
    effect(() => {
      const note = this.initialNote();
      if (note !== null && note !== this.noteControl.value) {
        this.noteControl.setValue(note, { emitEvent: false });
        this.currentLength.set(note.length);
      }
    });

    this.destroyRef.onDestroy(() => {
      this.subscription?.unsubscribe();
    });
  }

  public ngOnInit(): void {
    this.subscription = this.noteControl.valueChanges
      .subscribe((value: string) => {
        this.currentLength.set(value.length);
        const emitValue = value.trim().length > 0 ? value : null;
        this.noteChanged.emit(emitValue);
      });
  }
}
