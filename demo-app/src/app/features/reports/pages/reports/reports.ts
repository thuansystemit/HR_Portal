import { Component, OnInit, computed, inject } from '@angular/core';
import * as Highcharts from 'highcharts';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { ReportStore } from '../../store/report.store';

@Component({
  selector: 'app-reports',
  imports: [...SHARED_IMPORTS],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class ReportsPage implements OnInit {
  private readonly store = inject(ReportStore);

  protected readonly loading = this.store.loading;
  protected readonly error   = this.store.error;

  // ── Summary stats ────────────────────────────────────────────────────────
  protected readonly totalDocs = computed(() =>
    this.store.categoryCount().reduce((s, c) => s + c.count, 0),
  );

  protected readonly totalCategories = computed(() =>
    this.store.categoryCount().length,
  );

  protected readonly totalUsers = computed(() =>
    this.store.roleDistribution().reduce((s, r) => s + r.userCount, 0),
  );

  protected readonly totalStorageMb = computed(() => {
    const bytes = this.store.storage().reduce((s, c) => s + c.totalBytes, 0);
    return bytes === 0 ? '0 MB' : `${(bytes / 1_048_576).toFixed(1)} MB`;
  });

  // ── Chart 1: Upload trend (area spline) — last 12 months ─────────────────
  private readonly MONTH_LABELS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

  private last12Months(): string[] {
    const now = new Date();
    return Array.from({ length: 12 }, (_, i) => {
      const d = new Date(now.getFullYear(), now.getMonth() - 11 + i, 1);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    });
  }

  private monthLabel(yyyyMm: string): string {
    const [, m] = yyyyMm.split('-');
    return this.MONTH_LABELS[parseInt(m, 10) - 1];
  }

  protected readonly uploadTrendOptions = computed<Highcharts.Options>(() => {
    const months     = this.last12Months();
    const labels     = months.map(m => this.monthLabel(m));
    const rawEntries = this.store.uploadTrend();

    // Unique category names (preserving insertion order)
    const catNames = [...new Set(rawEntries.map(e => e.categoryName))];

    const series: Highcharts.SeriesAreasplineOptions[] = catNames.map(cat => ({
      type:  'areaspline',
      name:  cat,
      data:  months.map(m => {
        const found = rawEntries.find(e => e.categoryName === cat && e.month === m);
        return found?.count ?? 0;
      }),
    }));

    return {
      chart:    { type: 'areaspline', backgroundColor: 'transparent' },
      title:    { text: 'Document Upload Trend' },
      subtitle: { text: 'Last 12 months' },
      xAxis: {
        categories: labels,
        plotLines: [{
          value: 11, color: '#dc3545', width: 2, dashStyle: 'Dash', zIndex: 5,
          label: {
            text: 'Current month', rotation: 0, y: -8,
            style: { color: '#dc3545', fontSize: '11px', fontWeight: '600' },
          },
        }],
      },
      yAxis:   { title: { text: 'Uploads' }, min: 0, allowDecimals: false },
      tooltip: { shared: true, valueSuffix: ' docs' },
      plotOptions: { areaspline: { fillOpacity: 0.15, marker: { enabled: false } } },
      series,
      legend:  { enabled: catNames.length > 1 },
    };
  });

  // ── Chart 2: Documents per category (column) ─────────────────────────────
  protected readonly docsPerCategoryOptions = computed<Highcharts.Options>(() => {
    const data = this.store.categoryCount();
    return {
      chart:    { type: 'column', backgroundColor: 'transparent' },
      title:    { text: 'Documents per Category' },
      xAxis:    { categories: data.map(c => c.categoryName) },
      yAxis:    { title: { text: 'Documents' }, min: 0, allowDecimals: false },
      tooltip:  { valueSuffix: ' docs' },
      plotOptions: { column: { borderRadius: 4, dataLabels: { enabled: true } } },
      legend:   { enabled: false },
      series:   [{ type: 'column', name: 'Documents', data: data.map(c => c.count), colorByPoint: true }],
    };
  });

  // ── Chart 3: Role distribution (donut) ───────────────────────────────────
  protected readonly roleDistributionOptions = computed<Highcharts.Options>(() => {
    const data = this.store.roleDistribution();
    return {
      chart:   { type: 'pie', backgroundColor: 'transparent' },
      title:   { text: 'User Role Distribution' },
      tooltip: { pointFormat: '<b>{point.y} users</b> ({point.percentage:.0f}%)' },
      plotOptions: {
        pie: {
          innerSize: '55%',
          dataLabels: { enabled: true, format: '{point.name}: {point.y}' },
        },
      },
      legend: { enabled: false },
      series: [{
        type: 'pie', name: 'Users',
        data: data.map(r => ({ name: r.roleName, y: r.userCount })),
      }],
    };
  });

  // ── Chart 4: Storage by category (horizontal bar) ────────────────────────
  protected readonly storageByCategoryOptions = computed<Highcharts.Options>(() => {
    const data = this.store.storage();
    const toMb = (b: number) => parseFloat((b / 1_048_576).toFixed(2));
    return {
      chart:    { type: 'bar', backgroundColor: 'transparent' },
      title:    { text: 'Storage Used by Category' },
      subtitle: { text: 'Megabytes' },
      xAxis:    { categories: data.map(c => c.categoryName) },
      yAxis: {
        title:  { text: 'Size (MB)' },
        labels: { formatter() { return `${this.value} MB`; } },
      },
      tooltip: { valueSuffix: ' MB' },
      plotOptions: {
        bar: { borderRadius: 4, dataLabels: { enabled: true, format: '{y} MB' } },
      },
      legend: { enabled: false },
      series: [{
        type: 'bar', name: 'Storage',
        data: data.map(c => toMb(c.totalBytes)),
        colorByPoint: true,
      }],
    };
  });

  ngOnInit(): void {
    this.store.loadAll();
  }
}
