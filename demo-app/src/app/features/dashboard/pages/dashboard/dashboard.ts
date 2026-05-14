import { Component, OnInit, computed, inject } from '@angular/core';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { AuthService } from '../../../../auth/services/auth';
import { DocumentCategoryStore } from '../../../documents/store/document-category.store';
import { DocumentStore } from '../../../documents/store/document.store';

interface QuickCard {
  title:      string;
  desc:       string;
  icon:       string;
  color:      string;
  route:      string;
  permission?: boolean;
}

@Component({
  selector: 'app-dashboard',
  imports: [...SHARED_IMPORTS],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  protected readonly auth    = inject(AuthService);
  private   readonly catStore = inject(DocumentCategoryStore);
  private   readonly docStore = inject(DocumentStore);

  protected avatarUrl = computed(() => {
    const name = encodeURIComponent(this.auth.user()?.name ?? 'User');
    return `https://ui-avatars.com/api/?name=${name}&size=64&background=0d6efd&color=fff`;
  });

  protected visibleCategories = computed(() => {
    const user = this.auth.user();
    if (!user) return [];
    return this.catStore.categories().filter(cat => {
      const perm = cat.permissions.find(p => p.roleId === user.roleId);
      return perm?.canView ?? false;
    });
  });

  ngOnInit(): void {
    this.catStore.loadAll();
  }

  protected cards = computed<QuickCard[]>(() => [
    {
      title: 'User Management', desc: 'View and manage system users.',
      icon: 'bi-people-fill', color: 'primary', route: '/users',
      permission: this.auth.can('usersView'),
    },
    {
      title: 'Role Management', desc: 'Configure roles and permissions.',
      icon: 'bi-shield-lock-fill', color: 'warning', route: '/roles',
      permission: this.auth.can('rolesView'),
    },
    {
      title: 'Document Management', desc: 'Browse and manage document categories.',
      icon: 'bi-folder-fill', color: 'success', route: '/documents',
      permission: true,
    },
    {
      title: 'My Profile', desc: 'Update your name and password.',
      icon: 'bi-person-circle', color: 'info', route: '/profile',
      permission: true,
    },
  ].filter(c => c.permission));
}
