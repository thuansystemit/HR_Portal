import {
  Component, OnInit, TemplateRef, ViewChild, signal, computed,
} from '@angular/core';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn } from '../../../../shared/components/data-table/data-table.model';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { HiringRequestStore } from '../../store/hiring-request.store';
import { HiringRequest, RequestStatus } from '../../models/hiring-request.model';
import { AuthService } from '../../../../auth/services/auth';

@Component({
  selector: 'app-request-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './request-list.page.html',
  styleUrl: './request-list.page.scss',
})
export class RequestListPage implements OnInit {
  protected readonly store  = inject(HiringRequestStore);
  private  readonly router  = inject(Router);
  private  readonly auth    = inject(AuthService);

  @ViewChild('roleTypeTpl', { static: true }) roleTypeTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('urgencyTpl',  { static: true }) urgencyTpl!:  TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('statusTpl',   { static: true }) statusTpl!:   TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl',  { static: true }) actionsTpl!:  TemplateRef<{ $implicit: unknown; row: unknown }>;

  protected columns = signal<TableColumn[]>([]);
  protected get isHR(): boolean { return this.auth.can('hiringRequestsViewAll'); }

  protected readonly allStatuses: RequestStatus[] = [
    'PENDING', 'IN_PROGRESS', 'CANDIDATE_FOUND', 'HIRED', 'CLOSED', 'REJECTED',
  ];

  protected readonly selectedStatus = signal<RequestStatus | null>(null);

  protected readonly filteredRequests = computed<HiringRequest[]>(() => {
    const status = this.selectedStatus();
    const all    = this.store.requests();
    return status ? all.filter(r => r.status === status) : all;
  });

  protected statusCount(status: RequestStatus): number {
    return this.store.requests().filter(r => r.status === status).length;
  }

  ngOnInit(): void {
    this.isHR ? this.store.loadAll() : this.store.loadMine();
    this.columns.set([
      { key: 'roleType',      label: 'Role Type',   align: 'center', cellTemplate: this.roleTypeTpl },
      { key: 'title',         label: 'Title',       sortable: true, filterable: true },
      { key: 'department',    label: 'Department',  sortable: true, filterable: true },
      { key: 'urgency',       label: 'Urgency',     align: 'center', cellTemplate: this.urgencyTpl },
      { key: 'status',        label: 'Status',      align: 'center', cellTemplate: this.statusTpl },
      { key: 'requesterName', label: 'Requester',   sortable: true },
      { key: 'createdAt',     label: 'Created At',  sortable: true, formatter: appFormatDate },
      { key: 'actions',       label: 'Actions',     align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  viewRequest(row: unknown): void {
    const request = row as HiringRequest;
    this.router.navigate(['/hiring-requests', request.id]);
  }

  urgencyBadgeClass(urgency: string): string {
    switch (urgency) {
      case 'LOW':      return 'bg-secondary';
      case 'MEDIUM':   return 'bg-info text-dark';
      case 'HIGH':     return 'bg-warning text-dark';
      case 'CRITICAL': return 'bg-danger';
      default:         return 'bg-secondary';
    }
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'PENDING':         return 'bg-secondary';
      case 'IN_PROGRESS':     return 'bg-primary';
      case 'CANDIDATE_FOUND': return 'bg-info text-dark';
      case 'HIRED':           return 'bg-success';
      case 'CLOSED':          return 'bg-dark';
      case 'REJECTED':        return 'bg-danger';
      default:                return 'bg-secondary';
    }
  }

  roleTypeBadgeClass(roleType: string): string {
    switch (roleType) {
      case 'FRONTEND':  return 'bg-primary';
      case 'BACKEND':   return 'bg-success';
      case 'FULLSTACK': return 'bg-warning text-dark';
      default:          return 'bg-secondary';
    }
  }

  statusLabel(status: string): string {
    return status.replace(/_/g, ' ');
  }
}
