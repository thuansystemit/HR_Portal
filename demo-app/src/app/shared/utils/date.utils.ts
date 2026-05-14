import { formatDate } from '@angular/common';

export const APP_DATE_FORMAT = 'dd-MMM-yyyy';

export function appFormatDate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '';
  try {
    return formatDate(String(value), APP_DATE_FORMAT, 'en-US');
  } catch {
    return String(value);
  }
}
