import { Pipe, PipeTransform } from '@angular/core';
import { appFormatDate } from '../utils/date.utils';

@Pipe({ name: 'appDate', standalone: true })
export class AppDatePipe implements PipeTransform {
  transform(value: unknown): string {
    return appFormatDate(value);
  }
}
