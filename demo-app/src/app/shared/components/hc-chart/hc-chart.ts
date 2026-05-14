import {
  AfterViewInit, Component, ElementRef, OnDestroy,
  effect, input, viewChild,
} from '@angular/core';
import * as Highcharts from 'highcharts';

// Apply minimal global defaults once — consumers override via options
Highcharts.setOptions({
  credits:  { enabled: false },
  lang:     { thousandsSep: ',' },
  chart:    { style: { fontFamily: 'inherit' } },
  title:    { style: { fontWeight: '600', fontSize: '14px' } },
  subtitle: { style: { fontSize: '12px' } },
  colors: [
    '#0d6efd', '#198754', '#ffc107', '#dc3545',
    '#0dcaf0', '#6610f2', '#fd7e14', '#20c997',
  ],
  xAxis: {
    lineColor:  '#dee2e6',
    tickColor:  '#dee2e6',
    labels:     { style: { color: '#6c757d' } },
  },
  yAxis: {
    gridLineColor: '#e9ecef',
    labels:        { style: { color: '#6c757d' } },
    title:         { style: { color: '#6c757d' } },
  },
  legend:  { itemStyle: { fontWeight: '500', color: '#212529' } },
  tooltip: { borderRadius: 6, shadow: false },
  plotOptions: {
    series: { animation: { duration: 600 } },
  },
});

@Component({
  selector: 'app-hc-chart',
  standalone: true,
  imports: [],
  template: `<div #container class="w-100" [style.height]="height()"></div>`,
  styles: [`:host { display: block; width: 100%; }`],
})
export class HcChart implements AfterViewInit, OnDestroy {
  private readonly containerRef = viewChild.required<ElementRef<HTMLElement>>('container');

  readonly options = input.required<Highcharts.Options>();
  readonly height  = input<string>('350px');

  private chart?: Highcharts.Chart;
  private ro?: ResizeObserver;

  constructor() {
    // React to options changes AFTER the chart is created
    effect(() => {
      const opts = this.options();
      this.chart?.update(opts, true, true);
    });
  }

  ngAfterViewInit(): void {
    const el = this.containerRef().nativeElement;
    this.chart = Highcharts.chart(el, this.options());

    // Reflow when the container is resized (responsive layout)
    this.ro = new ResizeObserver(() => this.chart?.reflow());
    this.ro.observe(el);
  }

  ngOnDestroy(): void {
    this.ro?.disconnect();
    this.chart?.destroy();
    this.chart = undefined;
  }
}
