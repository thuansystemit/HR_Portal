import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LayoutService } from '../layout.service';
import { AuthService } from '../../auth/services/auth';
import { RolePermissions } from '../../features/roles/models/role.model';

interface NavItem {
  label:      string;
  icon:       string;
  route:      string;
  permission?: keyof RolePermissions;
}

const ALL_NAV: NavItem[] = [
  { label: 'Dashboard',           icon: 'bi-speedometer2', route: '/dashboard' },
  { label: 'Users',               icon: 'bi-people',       route: '/users',     permission: 'usersView' },
  { label: 'Roles',               icon: 'bi-shield-lock',  route: '/roles',     permission: 'rolesView' },
  { label: 'Document Management', icon: 'bi-file-text',    route: '/documents' },
  { label: 'Reports',             icon: 'bi-bar-chart',    route: '/reports',   permission: 'rolesView' }
  // { label: 'Settings',            icon: 'bi-gear',         route: '/settings',  permission: 'rolesEdit' },
];

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  protected readonly layout = inject(LayoutService);
  private  readonly auth   = inject(AuthService);

  protected navItems = computed(() =>
    ALL_NAV.filter(item =>
      !item.permission || this.auth.can(item.permission),
    ),
  );
}
