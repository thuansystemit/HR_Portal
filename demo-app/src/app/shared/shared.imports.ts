import { AsyncPipe, DecimalPipe, NgClass, NgStyle, TitleCasePipe } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AppDatePipe } from './pipes/app-date.pipe';
import { Divider } from './components/divider/divider';
import { DocPreview } from './components/doc-preview/doc-preview';
import { HcChart } from './components/hc-chart/hc-chart';
import { AppButton } from './components/app-button/app-button';
import { PageLayout } from './components/page-layout/page-layout';
import { FormCard } from './components/form-card/form-card';
import { DataTable } from './components/data-table/data-table';
import { Dropdown } from './components/dropdown/dropdown';
import { Pagination } from './components/pagination/pagination';

export const SHARED_IMPORTS = [
  // Angular common
  AsyncPipe,
  DecimalPipe,
  NgClass,
  NgStyle,
  TitleCasePipe,
  // Forms
  FormsModule,
  ReactiveFormsModule,
  // Router
  RouterLink,
  RouterLinkActive,
  // Shared UI
  PageLayout,
  FormCard,
  DataTable,
  Dropdown,
  Pagination,
  Divider,
  DocPreview,
  HcChart,
  AppButton,
  // Shared pipes
  AppDatePipe,
] as const;
