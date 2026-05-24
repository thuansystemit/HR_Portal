import { Component, OnInit, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { KnowledgeStore } from '../../store/knowledge.store';
import { EntityType } from '../../models/knowledge.model';

@Component({
  selector: 'app-entity-detail',
  imports: [...SHARED_IMPORTS],
  templateUrl: './entity-detail.page.html',
  styleUrl: './entity-detail.page.scss',
})
export class EntityDetailPage implements OnInit {
  entityId = input<string>('');

  protected readonly store  = inject(KnowledgeStore);
  private  readonly router = inject(Router);

  protected readonly entity = this.store.selected;

  ngOnInit(): void {
    this.store.loadById(this.entityId());
  }

  goBack(): void {
    this.router.navigate(['/knowledge']);
  }

  entityTypeColor(type: EntityType): string {
    switch (type) {
      case 'Technology': return 'bg-primary';
      case 'Concept':    return 'bg-success';
      case 'Person':     return 'bg-warning text-dark';
      case 'Project':    return 'bg-info';
      case 'Team':       return 'bg-secondary';
      default:           return 'bg-secondary';
    }
  }

  propertiesEntries(props: Record<string, unknown> | null): Array<{ key: string; value: string }> {
    if (!props) return [];
    return Object.entries(props).map(([key, value]) => ({
      key,
      value: value === null || value === undefined ? '' : String(value),
    }));
  }
}
