import { RolePermissions } from '../../features/roles/models/role.model';

export interface AuthUser {
  id:          string;
  name:        string;       // mapped from backend `fullName`
  email:       string;
  roleId:      string;
  roleName:    string;
  permissions: RolePermissions;
}

export interface LoginCredentials {
  email:    string;
  password: string;
}
