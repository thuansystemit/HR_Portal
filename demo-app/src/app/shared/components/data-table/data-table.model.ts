import { TemplateRef } from '@angular/core';
import { DropdownOption } from '../dropdown/dropdown.model';

export interface TableColumn {
  key:            string;
  label:          string;
  sortable?:      boolean;
  filterable?:    boolean;
  filterType?:    'text' | 'dropdown';   // default: 'text'
  filterOptions?: DropdownOption[];       // required when filterType = 'dropdown'
  align?:         'start' | 'center' | 'end';
  formatter?:     (value: unknown, row: unknown) => string;
  cellTemplate?:  TemplateRef<{ $implicit: unknown; row: unknown }>;
}

export type SortDirection = 'asc' | 'desc' | null;

export interface SortState {
  key:       string;
  direction: SortDirection;
}

export interface PageEvent {
  page:     number;
  pageSize: number;
  total:    number;
}
