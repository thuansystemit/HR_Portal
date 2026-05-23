import { Component, OnInit, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { InvoiceRecordStore } from '../../store/invoice-record.store';

@Component({
  selector: 'app-invoice-detail',
  imports: [...SHARED_IMPORTS],
  templateUrl: './invoice-detail.page.html',
  styleUrl:    './invoice-detail.page.scss',
})
export class InvoiceDetailPage implements OnInit {
  categoryId = input<string>('');
  recordId   = input<string>('');

  protected readonly store  = inject(InvoiceRecordStore);
  private  readonly router = inject(Router);

  protected readonly record = this.store.selected;

  ngOnInit(): void {
    this.store.loadById(this.recordId());
  }

  goBack(): void {
    this.router.navigate(['/invoice-records', this.categoryId()]);
  }
}
