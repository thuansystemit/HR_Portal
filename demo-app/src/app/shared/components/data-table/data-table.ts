import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { TableColumn, SortState, PageEvent } from './data-table.model';
import { Dropdown } from '../dropdown/dropdown';
import { DropdownOption } from '../dropdown/dropdown.model';

@Component({
  selector: 'app-data-table',
  imports: [FormsModule, NgTemplateOutlet, Dropdown],
  templateUrl: './data-table.html',
  styleUrl: './data-table.scss',
})
export class DataTable {
  columns         = input.required<TableColumn[]>();
  data            = input.required<unknown[]>();
  loading         = input<boolean>(false);
  emptyMessage    = input<string>('No records found.');
  pageSize        = input<number>(10);
  /** Set false to hide the built-in pagination bar (use <app-pagination> outside instead). */
  showPagination  = input<boolean>(true);
  /** Drive the current page from outside. Syncs with the internal page signal. */
  controlledPage  = input<number | undefined>(undefined);

  rowClick     = output<unknown>();
  pageChange   = output<PageEvent>();
  /** Fires whenever page, total, or pageSize changes — lets the host sync <app-pagination>. */
  stateChange  = output<PageEvent>();

  protected page       = signal(1);
  protected sortState  = signal<SortState>({ key: '', direction: null });
  protected colFilters = signal<Record<string, unknown>>({});

  constructor() {
    // Sync externally-controlled page into the internal signal
    effect(() => {
      const p = this.controlledPage();
      if (p !== undefined && p !== this.page()) this.page.set(p);
    }, { allowSignalWrites: true });

    // Broadcast state to the host whenever page / total / pageSize changes
    effect(() => {
      this.stateChange.emit({
        page:     this.page(),
        pageSize: this.pageSize(),
        total:    this.total(),
      });
    });
  }

  protected hasFilters         = computed(() => this.columns().some(c => c.filterable));
  protected hasDropdownFilters = computed(() => this.columns().some(c => c.filterable && c.filterType === 'dropdown'));

  protected filteredData = computed(() => {
    const filters = this.colFilters();
    const colMap  = new Map(this.columns().map(c => [c.key, c]));
    const { key, direction } = this.sortState();

    let rows = (this.data() as Record<string, unknown>[]).filter(row =>
      Object.entries(filters).every(([colKey, filterVal]) => {
        if (filterVal === '' || filterVal === null || filterVal === undefined) return true;
        const col    = colMap.get(colKey);
        const rowVal = String(row[colKey] ?? '');
        return col?.filterType === 'dropdown'
          ? rowVal === String(filterVal)
          : rowVal.toLowerCase().includes(String(filterVal).toLowerCase());
      })
    );

    if (key && direction) {
      rows = [...rows].sort((a, b) => {
        const av = String(a[key] ?? '');
        const bv = String(b[key] ?? '');
        return direction === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
      });
    }

    return rows;
  });

  protected total     = computed(() => this.filteredData().length);
  protected pageCount = computed(() => Math.ceil(this.total() / this.pageSize()) || 1);

  protected pagedData = computed(() => {
    if (this.pageSize() === 0) return this.filteredData();
    const start = (this.page() - 1) * this.pageSize();
    return this.filteredData().slice(start, start + this.pageSize());
  });

  // Used by the built-in pagination bar only
  protected pageNumbers = computed(() =>
    Array.from({ length: this.pageCount() }, (_, i) => i + 1)
  );
  protected startRow = computed(() =>
    this.total() === 0 ? 0 : (this.page() - 1) * this.pageSize() + 1
  );
  protected endRow = computed(() =>
    Math.min(this.page() * this.pageSize(), this.total())
  );

  sort(key: string): void {
    this.page.set(1);
    this.sortState.update(s => {
      if (s.key !== key)         return { key, direction: 'asc' };
      if (s.direction === 'asc') return { key, direction: 'desc' };
      return { key: '', direction: null };
    });
  }

  setFilter(key: string, value: unknown): void {
    this.page.set(1);
    this.colFilters.update(f => ({ ...f, [key]: value }));
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.pageChange.emit({ page: p, pageSize: this.pageSize(), total: this.total() });
  }

  sortIcon(key: string): string {
    const { key: k, direction } = this.sortState();
    if (k !== key) return 'bi-arrow-down-up';
    return direction === 'asc' ? 'bi-sort-up text-primary' : 'bi-sort-down text-primary';
  }

  cellValue(row: Record<string, unknown>, col: TableColumn): string {
    const raw = row[col.key];
    return col.formatter ? col.formatter(raw, row) : String(raw ?? '');
  }

  onRowClick(row: unknown): void { this.rowClick.emit(row); }

  dropdownFilterOptions(col: TableColumn): DropdownOption[] {
    return [{ label: 'All', value: '' }, ...(col.filterOptions ?? [])];
  }

  filterValue(key: string): unknown {
    return this.colFilters()[key] ?? '';
  }
}
