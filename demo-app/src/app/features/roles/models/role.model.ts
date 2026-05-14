export interface RolePermissions {
  usersView:   boolean;
  usersCreate: boolean;
  usersEdit:   boolean;
  usersDelete: boolean;
  rolesView:   boolean;
  rolesEdit:   boolean;
}

export interface Role {
  id:          string;
  name:        string;
  description: string;
  permissions: RolePermissions;
  userCount:   number;
  builtIn:     boolean;
  createdAt:   string;
}

export interface RoleDto {
  name:        string;
  description: string;
  permissions: RolePermissions;
}

export const PERMISSION_LABELS: Record<keyof RolePermissions, string> = {
  usersView:   'View Users',
  usersCreate: 'Create Users',
  usersEdit:   'Edit Users',
  usersDelete: 'Delete Users',
  rolesView:   'View Roles',
  rolesEdit:   'Edit Roles',
};

export const PERMISSION_GROUPS: { label: string; keys: (keyof RolePermissions)[] }[] = [
  { label: 'Users', keys: ['usersView', 'usersCreate', 'usersEdit', 'usersDelete'] },
  { label: 'Roles', keys: ['rolesView', 'rolesEdit'] },
];

export const TOTAL_PERMISSIONS = Object.keys(PERMISSION_LABELS).length;
