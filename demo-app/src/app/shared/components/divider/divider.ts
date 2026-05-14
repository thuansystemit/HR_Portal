import { Component, HostBinding, computed, input } from '@angular/core';
import { NgClass } from '@angular/common';

export type DividerVariant = 'solid' | 'dashed' | 'dotted';
export type DividerSpacing = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-divider',
  imports: [NgClass],
  templateUrl: './divider.html',
  styleUrl: './divider.scss',
})
export class Divider {
  label     = input<string>('');
  variant   = input<DividerVariant>('solid');
  spacing   = input<DividerSpacing>('md');
  thickness = input<string>('1px');

  @HostBinding('style.--dvd-thickness')
  get thicknessVar(): string { return this.thickness(); }

  protected hostClass = computed(() => [
    `dvd-${this.variant()}`,
    `dvd-${this.spacing()}`,
  ]);
}
