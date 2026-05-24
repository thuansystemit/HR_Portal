import {
  Component, ElementRef, HostListener, Injector,
  OnInit, computed, forwardRef, inject, input, signal,
} from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, NgControl } from '@angular/forms';
import { DropdownOption } from './dropdown.model';

@Component({
  selector: 'app-dropdown',
  imports: [FormsModule],
  templateUrl: './dropdown.html',
  styleUrl: './dropdown.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => Dropdown),
      multi: true,
    },
  ],
})
export class Dropdown implements ControlValueAccessor, OnInit {
  options     = input.required<DropdownOption[]>();
  placeholder = input<string>('Select…');
  searchable  = input<boolean>(false);

  private readonly elRef    = inject(ElementRef);
  private readonly injector = inject(Injector);
  private ngControl: NgControl | null = null;

  protected isOpen      = signal(false);
  protected isDisabled  = signal(false);
  protected value       = signal<unknown>(null);
  protected searchTerm  = signal('');

  private onChange  = (_: unknown) => {};
  private onTouched = () => {};

  protected selectedLabel = computed(() => {
    const match = this.options().find(o => o.value === this.value());
    return match?.label ?? '';
  });

  protected hasValue = computed(() =>
    this.value() !== null && this.value() !== '' && this.value() !== undefined
  );

  protected filteredOptions = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    if (!term) return this.options();
    return this.options().filter(o => o.label.toLowerCase().includes(term));
  });

  protected get isInvalid(): boolean {
    const c = this.ngControl?.control;
    return !!(c?.invalid && c.touched);
  }

  ngOnInit(): void {
    // Delayed to avoid circular injection with NG_VALUE_ACCESSOR
    this.ngControl = this.injector.get(NgControl, null);
  }

  toggle(): void {
    if (this.isDisabled()) return;
    if (this.isOpen()) {
      this.close();
    } else {
      this.searchTerm.set('');
      this.isOpen.set(true);
    }
  }

  select(opt: DropdownOption): void {
    if (opt.disabled) return;
    this.value.set(opt.value);
    this.onChange(opt.value);
    this.onTouched();
    this.close();
  }

  protected onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  private close(): void {
    this.isOpen.set(false);
    this.onTouched();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      if (this.isOpen()) this.close();
    }
  }

  @HostListener('keydown.escape')
  onEscape(): void {
    if (this.isOpen()) this.close();
  }

  // ControlValueAccessor
  writeValue(val: unknown): void        { this.value.set(val ?? null); }
  registerOnChange(fn: (_: unknown) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void          { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void        { this.isDisabled.set(disabled); }
}
