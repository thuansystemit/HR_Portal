import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LayoutService } from '../layout.service';
import { AuthService } from '../../auth/services/auth';
import { RolePermissions } from '../../features/roles/models/role.model';

interface NavItem {
  label:       string;
  icon?:       string;
  route?:      string;
  permission?: keyof RolePermissions;   // show only if user HAS this permission
  hiddenFor?:  keyof RolePermissions;   // hide if user HAS this permission
  section?:    true;
}

const ALL_NAV: NavItem[] = [
  { label: 'Dashboard',          icon: 'bi-speedometer2',   route: '/dashboard' },

  { label: 'Hiring',             section: true },
  { label: 'Hiring Requests',    icon: 'bi-send',           route: '/hiring-requests',         permission: 'hiringRequestsViewAll' },
  { label: 'Candidates',         icon: 'bi-people-fill',    route: '/cv-candidates/search',    permission: 'candidateSearch' },
  { label: 'Job Postings',       icon: 'bi-briefcase',      route: '/recruitment',             permission: 'recruitmentManage' },
  { label: 'Pipeline Analytics', icon: 'bi-graph-up-arrow', route: '/hr-analytics',            permission: 'analyticsView' },

  { label: 'Dev Team',           section: true },
  { label: 'My Requests',        icon: 'bi-send-check',     route: '/hiring-requests',         permission: 'hiringRequestsSubmit', hiddenFor: 'hiringRequestsViewAll' },
  { label: 'Shared CVs',         icon: 'bi-inbox',          route: '/cv-shares/inbox',         permission: 'cvSharesReceive' },

  { label: 'Content',            section: true },
  { label: 'Documents',          icon: 'bi-file-text',      route: '/documents',               hiddenFor: 'cvSharesReceive' },
  { label: 'Knowledge Base',     icon: 'bi-diagram-3',      route: '/knowledge',               hiddenFor: 'cvSharesReceive' },

  { label: 'Administration',     section: true },
  { label: 'Users',              icon: 'bi-people',         route: '/users',   permission: 'usersView' },
  { label: 'Roles',              icon: 'bi-shield-lock',    route: '/roles',   permission: 'rolesView' },
  { label: 'Reports',            icon: 'bi-bar-chart',      route: '/reports', permission: 'rolesView' },
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

  protected navItems = computed(() => {
    // First pass: filter individual nav items
    const visible = ALL_NAV.filter(item => {
      if (item.section) return true;  // sections evaluated in second pass
      if (item.permission && !this.auth.can(item.permission))  return false;
      if (item.hiddenFor &&  this.auth.can(item.hiddenFor))    return false;
      return true;
    });

    // Second pass: remove section headers that have no visible items following them
    return visible.filter((item, idx) => {
      if (!item.section) return true;
      // Look ahead: is there at least one non-section item before the next section?
      for (let i = idx + 1; i < visible.length; i++) {
        if (visible[i].section) break;
        return true;  // found a visible child item
      }
      return false;  // no children — hide this section header
    });
  });
}
