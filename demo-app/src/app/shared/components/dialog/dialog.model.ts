import { Type } from '@angular/core';

export type DialogType = 'info' | 'success' | 'warning' | 'danger';
export type DialogSize = 'sm' | 'md' | 'lg' | 'xl';

export interface DialogConfig {
  title:          string;
  message?:       string;
  type?:          DialogType;   // default: 'info'
  size?:          DialogSize;   // default: 'md'
  confirmLabel?:  string;       // default: 'Confirm'
  cancelLabel?:   string;       // default: 'Cancel'
  showCancel?:    boolean;      // default: true
  bodyComponent?: Type<unknown>;
  bodyInputs?:    Record<string, unknown>;
}

/**
 * Implement on components rendered inside a form dialog.
 * Return true to close, false to keep the dialog open (e.g. validation failed).
 */
export interface DialogFormBody {
  onConfirm(): Promise<boolean>;
}
