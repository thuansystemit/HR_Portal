import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { CvShareApi } from '../../services/cv-share.api';
import { CvShare } from '../../models/cv-share.model';

@Component({
  selector: 'app-cv-share-inbox',
  imports: [...SHARED_IMPORTS],
  templateUrl: './cv-share-inbox.page.html',
  styleUrl:    './cv-share-inbox.page.scss',
})
export class CvShareInboxPage implements OnInit {
  private readonly api    = inject(CvShareApi);
  private readonly router = inject(Router);

  protected shares  = signal<CvShare[]>([]);
  protected loading = signal(false);
  protected error   = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.api.inbox().subscribe({
      next: data => { this.shares.set(data); this.loading.set(false); },
      error: ()   => { this.error.set('Could not load shared CVs.'); this.loading.set(false); },
    });
  }

  protected viewShare(share: CvShare): void {
    this.router.navigate(['/cv-shares', share.id]);
  }

  protected impressionBadge(imp: string | null): string {
    if (!imp) return 'bg-secondary';
    return { INTERESTED: 'bg-success', NOT_INTERESTED: 'bg-danger', REVIEW_LATER: 'bg-warning text-dark' }[imp] ?? 'bg-secondary';
  }

  protected impressionLabel(imp: string | null): string {
    if (!imp) return 'Pending Review';
    return imp.replace(/_/g, ' ');
  }

  protected hiringStatusBadge(status: string | null): string {
    const map: Record<string, string> = {
      IN_PROCESS: 'badge bg-info text-dark',
      OFFERED:    'badge bg-warning text-dark',
      HIRED:      'badge bg-success',
      REJECTED:   'badge bg-danger',
      WITHDRAWN:  'badge bg-dark',
    };
    return map[status ?? ''] ?? 'badge bg-secondary';
  }

  protected hiringStatusLabel(status: string | null): string {
    const map: Record<string, string> = {
      AVAILABLE:  'Available',
      IN_PROCESS: 'In Process',
      OFFERED:    'Offered',
      HIRED:      'Hired',
      REJECTED:   'Rejected',
      WITHDRAWN:  'Withdrawn',
    };
    return map[status ?? ''] ?? (status ?? '');
  }
}
