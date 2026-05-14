import { Component, computed, input, output } from '@angular/core';
import { NgClass } from '@angular/common';

export type PageItem = number | '...';

@Component({
  selector: 'app-pagination',
  imports: [NgClass],
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss',
})
export class Pagination {
  /** Current active page (1-based). */
  page      = input.required<number>();
  /** Total number of pages. */
  pageCount = input.required<number>();
  /**
   * Optional: total item count.
   * When provided together with pageSize, renders "Showing X–Y of Z records".
   */
  total    = input<number | undefined>(undefined);
  pageSize = input<number | undefined>(undefined);

  pageChange = output<number>();

  // ── Visible page buttons with ellipsis ─────────────────────────
  protected visiblePages = computed((): PageItem[] => {
    const count   = this.pageCount();
    const current = this.page();

    if (count <= 7) {
      return Array.from({ length: count }, (_, i) => i + 1);
    }

    const delta = 2;
    const left  = Math.max(2, current - delta);
    const right = Math.min(count - 1, current + delta);
    const pages: PageItem[] = [1];

    if (left > 2)       pages.push('...');
    for (let i = left; i <= right; i++) pages.push(i);
    if (right < count - 1) pages.push('...');
    pages.push(count);

    return pages;
  });

  // ── Optional record range summary ──────────────────────────────
  protected startRow = computed(() => {
    const { total, pageSize } = this.rangeInputs();
    if (total == null || !pageSize) return null;
    return total === 0 ? 0 : (this.page() - 1) * pageSize + 1;
  });

  protected endRow = computed(() => {
    const { total, pageSize } = this.rangeInputs();
    if (total == null || !pageSize) return null;
    return Math.min(this.page() * pageSize, total);
  });

  private rangeInputs = computed(() => ({
    total:    this.total(),
    pageSize: this.pageSize(),
  }));

  protected isEllipsis(item: PageItem): item is '...' {
    return item === '...';
  }

  go(p: number): void {
    if (p < 1 || p > this.pageCount() || p === this.page()) return;
    this.pageChange.emit(p);
  }
}
