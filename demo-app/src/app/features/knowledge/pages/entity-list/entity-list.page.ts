import {
  Component, OnInit, TemplateRef, ViewChild, computed, inject, signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { TableColumn, PageEvent } from '../../../../shared/components/data-table/data-table.model';
import { appFormatDate } from '../../../../shared/utils/date.utils';
import { KnowledgeStore } from '../../store/knowledge.store';
import { EntityType, KnowledgeEntitySummary } from '../../models/knowledge.model';

@Component({
  selector: 'app-entity-list',
  imports: [...SHARED_IMPORTS],
  templateUrl: './entity-list.page.html',
  styleUrl: './entity-list.page.scss',
})
export class EntityListPage implements OnInit {
  protected readonly store  = inject(KnowledgeStore);
  private  readonly router = inject(Router);

  @ViewChild('typeTpl',    { static: true }) typeTpl!:    TemplateRef<{ $implicit: unknown; row: unknown }>;
  @ViewChild('actionsTpl', { static: true }) actionsTpl!: TemplateRef<{ $implicit: unknown; row: unknown }>;

  readonly pageSize = 20;
  protected q            = signal('');
  protected selectedType = signal('');
  protected page         = signal(1);
  protected pageCount    = computed(() => Math.ceil(this.store.totalElements() / this.pageSize) || 1);
  protected columns      = signal<TableColumn[]>([]);

  readonly entityTypes: EntityType[] = ['Technology', 'Concept', 'Person', 'Project', 'Team'];

  ngOnInit(): void {
    this.doSearch();
    this.columns.set([
      { key: 'name',        label: 'Name',     sortable: true, filterable: true },
      { key: 'entityType',  label: 'Type',     cellTemplate: this.typeTpl },
      {
        key: 'aliases',
        label: 'Aliases',
        formatter: (v) => (v as string[]).join(', '),
      },
      { key: 'createdAt',   label: 'Added',    formatter: appFormatDate },
      { key: 'actions',     label: 'Actions',  align: 'center', cellTemplate: this.actionsTpl },
    ]);
  }

  protected doSearch(): void {
    this.store.search(this.q(), this.selectedType(), this.page() - 1);
  }

  protected onPageChange(p: number): void {
    this.page.set(p);
    this.doSearch();
  }

  protected viewEntity(row: unknown): void {
    const entity = row as KnowledgeEntitySummary;
    this.router.navigate(['/knowledge', entity.id]);
  }

  protected entityTypeBadgeClass(type: EntityType | unknown): string {
    switch (type as EntityType) {
      case 'Technology': return 'bg-primary';
      case 'Concept':    return 'bg-success';
      case 'Person':     return 'bg-warning text-dark';
      case 'Project':    return 'bg-info';
      case 'Team':       return 'bg-secondary';
      default:           return 'bg-secondary';
    }
  }
}
