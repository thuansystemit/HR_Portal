import { Component, OnInit, computed, inject } from '@angular/core';
import * as Highcharts from 'highcharts';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { HrAnalyticsStore } from '../../store/hr-analytics.store';

const STAGE_ORDER = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED'];
const STAGE_COLORS: Record<string, string> = {
  APPLIED:   '#0d6efd',
  SCREENING: '#6f42c1',
  INTERVIEW: '#fd7e14',
  OFFER:     '#20c997',
  HIRED:     '#198754',
  REJECTED:  '#dc3545',
};

@Component({
  selector: 'app-hr-analytics',
  imports: [...SHARED_IMPORTS],
  templateUrl: './hr-analytics.page.html',
  styleUrl:    './hr-analytics.page.scss',
})
export class HrAnalyticsPage implements OnInit {
  protected readonly store   = inject(HrAnalyticsStore);
  readonly Highcharts         = Highcharts;

  // ── Summary KPIs ─────────────────────────────────────────────────────────

  protected readonly totalApplications = computed(() =>
    this.store.funnel().reduce((s, e) => s + e.count, 0),
  );

  protected readonly totalHired = computed(() =>
    this.store.funnel().find(e => e.stage === 'HIRED')?.count ?? 0,
  );

  protected readonly overallHireRate = computed(() => {
    const total = this.totalApplications();
    return total === 0 ? '0%' : `${Math.round(this.totalHired() / total * 100)}%`;
  });

  protected readonly avgTimeToHire = computed(() => {
    const data = this.store.timeToHire();
    if (!data.length) return '—';
    const avg = data.reduce((s, e) => s + e.avgDays, 0) / data.length;
    return `${avg.toFixed(1)} days`;
  });

  // ── AN-1: Recruitment Funnel (horizontal bar) ─────────────────────────────

  protected readonly funnelOptions = computed<Highcharts.Options>(() => {
    const data = [...this.store.funnel()].sort(
      (a, b) => STAGE_ORDER.indexOf(a.stage) - STAGE_ORDER.indexOf(b.stage),
    );
    return {
      chart:  { type: 'bar', backgroundColor: 'transparent' },
      title:  { text: 'Recruitment Funnel' },
      subtitle: { text: 'Candidates per pipeline stage' },
      xAxis:  { categories: data.map(e => e.stage), reversed: false },
      yAxis:  { title: { text: 'Candidates' }, min: 0, allowDecimals: false },
      tooltip: { valueSuffix: ' candidates' },
      plotOptions: {
        bar: {
          borderRadius: 4,
          dataLabels: { enabled: true },
          colorByPoint: true,
          colors: data.map(e => STAGE_COLORS[e.stage] ?? '#6c757d'),
        },
      },
      legend: { enabled: false },
      series: [{ type: 'bar', name: 'Candidates', data: data.map(e => e.count) }],
    };
  });

  // ── AN-2: Time-to-Hire (column) ───────────────────────────────────────────

  protected readonly timeToHireOptions = computed<Highcharts.Options>(() => {
    const data = this.store.timeToHire();
    return {
      chart:  { type: 'column', backgroundColor: 'transparent' },
      title:  { text: 'Average Time to Hire' },
      subtitle: { text: 'Days from application to offer accepted (top 10 job postings)' },
      xAxis:  { categories: data.map(e => e.jobTitle), labels: { rotation: -25 } },
      yAxis:  { title: { text: 'Days' }, min: 0 },
      tooltip: { valueSuffix: ' days' },
      plotOptions: {
        column: {
          borderRadius: 4,
          dataLabels: { enabled: true, format: '{y}d' },
          color: '#0d6efd',
        },
      },
      legend: { enabled: false },
      series: [{ type: 'column', name: 'Avg Days', data: data.map(e => e.avgDays) }],
    };
  });

  // ── AN-3: Top Skills (horizontal bar) ─────────────────────────────────────

  protected readonly topSkillsOptions = computed<Highcharts.Options>(() => {
    const data = [...this.store.topSkills()].reverse();
    return {
      chart:  { type: 'bar', backgroundColor: 'transparent' },
      title:  { text: 'Top Skills in Candidate Pool' },
      subtitle: { text: 'Unique candidates per skill (top 15)' },
      xAxis:  { categories: data.map(e => e.skillName) },
      yAxis:  { title: { text: 'Candidates' }, min: 0, allowDecimals: false },
      tooltip: { valueSuffix: ' candidates' },
      plotOptions: {
        bar: {
          borderRadius: 4,
          dataLabels: { enabled: true },
          color: '#6f42c1',
        },
      },
      legend: { enabled: false },
      series: [{ type: 'bar', name: 'Candidates', data: data.map(e => e.candidateCount) }],
    };
  });

  // ── AN-4: Application Volume Trend (area) ─────────────────────────────────

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

  protected readonly trendOptions = computed<Highcharts.Options>(() => {
    const months  = this.last12Months();
    const entries = this.store.applicationTrend();
    const data    = months.map(m => entries.find(e => e.month === m)?.count ?? 0);
    return {
      chart:  { type: 'areaspline', backgroundColor: 'transparent' },
      title:  { text: 'Application Volume Trend' },
      subtitle: { text: 'Last 12 months' },
      xAxis: {
        categories: months.map(m => this.monthLabel(m)),
        plotLines: [{
          value: 11, color: '#dc3545', width: 2, dashStyle: 'Dash', zIndex: 5,
          label: { text: 'Now', rotation: 0, y: -8, style: { color: '#dc3545', fontSize: '11px' } },
        }],
      },
      yAxis:  { title: { text: 'Applications' }, min: 0, allowDecimals: false },
      tooltip: { valueSuffix: ' applications' },
      plotOptions: { areaspline: { fillOpacity: 0.15, marker: { enabled: true, radius: 4 }, color: '#20c997' } },
      legend: { enabled: false },
      series: [{ type: 'areaspline', name: 'Applications', data }],
    };
  });

  // ── AN-5: Stage Conversion Rates (column) ─────────────────────────────────

  protected readonly conversionOptions = computed<Highcharts.Options>(() => {
    const data = this.store.conversionRates();
    return {
      chart:  { type: 'column', backgroundColor: 'transparent' },
      title:  { text: 'Stage Conversion Rates' },
      subtitle: { text: '% of candidates advancing to the next stage' },
      xAxis:  { categories: data.map(e => `${e.fromStage} → ${e.toStage}`) },
      yAxis:  { title: { text: '%' }, min: 0, max: 100, labels: { format: '{value}%' } },
      tooltip: { valueSuffix: '%' },
      plotOptions: {
        column: {
          borderRadius: 4,
          dataLabels: { enabled: true, format: '{y}%' },
          colorByPoint: true,
          colors: ['#0d6efd','#6f42c1','#fd7e14','#20c997'],
        },
      },
      legend: { enabled: false },
      series: [{ type: 'column', name: 'Conversion', data: data.map(e => e.rate) }],
    };
  });

  ngOnInit(): void {
    this.store.loadAll();
  }
}
