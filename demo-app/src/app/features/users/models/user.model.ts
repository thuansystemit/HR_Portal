export type UserRole   = 'admin' | 'manager' | 'viewer';
export type UserStatus = 'active' | 'inactive' | 'pending';

export interface User {
  id:        string;
  name:      string;
  email:     string;
  role:      UserRole;
  roleId:    string;
  status:    UserStatus;
  createdAt: string;
}

export interface UserDto {
  name:      string;
  email:     string;
  role:      UserRole;
  roleId:    string;
  status:    UserStatus;
  password?: string;    // required on create, omitted on update
}
