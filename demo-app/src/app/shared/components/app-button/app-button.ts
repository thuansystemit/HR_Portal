import { Component, computed, input, output } from '@angular/core';
import { NgClass } from '@angular/common';

export type BtnVariant =
  | 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info'
  | 'light'   | 'dark'      | 'link'
  | 'outline-primary'   | 'outline-secondary' | 'outline-success'
  | 'outline-danger'    | 'outline-warning'   | 'outline-info';

export type BtnSize = 'sm' | 'lg' | '';

@Component({
  selector: 'app-btn',
  imports: [NgClass],
  templateUrl: './app-button.html',
  styleUrl: './app-button.scss',
})
export class AppButton {
  // ── Inputs ──────────────────────────────────────────────────────────────
  readonly variant  = input<BtnVariant>('primary');
  readonly size     = input<BtnSize>('');
  readonly type     = input<'button' | 'submit' | 'reset'>('button');

  readonly icon     = input('');   // Bootstrap icon class e.g. 'bi-save'
  readonly iconEnd  = input('');   // trailing icon
  readonly iconOnly = input(false); // icon-only: suppresses icon margin

  readonly loading  = input(false);
  readonly disabled = input(false);
  readonly block    = input(false);  // w-100
  readonly pill     = input(false);  // rounded-pill
  readonly flat     = input(false);  // border-0 (e.g. toolbar icon buttons)

  // ── Outputs ─────────────────────────────────────────────────────────────
  readonly btnClick = output<MouseEvent>();

  // ── Derived ─────────────────────────────────────────────────────────────
  protected readonly isDisabled = computed(() => this.loading() || this.disabled());

  protected readonly btnClass = computed(() => {
    const cls: string[] = ['btn', `btn-${this.variant()}`];
    if (this.size())   cls.push(`btn-${this.size()}`);
    if (this.block())  cls.push('w-100');
    if (this.pill())   cls.push('rounded-pill');
    if (this.flat())   cls.push('border-0');
    return cls.join(' ');
  });

  protected handleClick(e: MouseEvent): void {
    if (this.isDisabled()) { e.stopPropagation(); return; }
    this.btnClick.emit(e);
  }
}
