export interface RolePermissions {
  // IAM
  usersView:                 boolean;
  usersCreate:               boolean;
  usersEdit:                 boolean;
  usersDelete:               boolean;
  rolesView:                 boolean;
  rolesEdit:                 boolean;
  // Hiring Requests
  hiringRequestsSubmit:      boolean;
  hiringRequestsViewOwn:     boolean;
  hiringRequestsViewAll:     boolean;
  hiringRequestsManage:      boolean;
  // CV Sharing
  cvSharesSend:              boolean;
  cvSharesReceive:           boolean;
  cvSharesSubmitImpression:  boolean;
  // Recruitment
  recruitmentBoardView:      boolean;
  recruitmentManage:         boolean;
  interviewFeedbackSubmit:   boolean;
  // Candidates
  candidateSearch:           boolean;
  // Analytics
  analyticsView:             boolean;
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
  usersView:                'View Users',
  usersCreate:              'Create Users',
  usersEdit:                'Edit Users',
  usersDelete:              'Delete Users',
  rolesView:                'View Roles',
  rolesEdit:                'Edit Roles',
  hiringRequestsSubmit:     'Submit Hiring Requests',
  hiringRequestsViewOwn:    'View Own Requests',
  hiringRequestsViewAll:    'View All Requests',
  hiringRequestsManage:     'Manage Request Status',
  cvSharesSend:             'Share CVs',
  cvSharesReceive:          'Receive Shared CVs',
  cvSharesSubmitImpression: 'Submit CV Impression',
  recruitmentBoardView:     'View Kanban Board',
  recruitmentManage:        'Manage Recruitment Pipeline',
  interviewFeedbackSubmit:  'Submit Interview Feedback',
  candidateSearch:          'Search Candidates',
  analyticsView:            'View Analytics',
};

export const PERMISSION_GROUPS: { label: string; keys: (keyof RolePermissions)[] }[] = [
  { label: 'Users',            keys: ['usersView', 'usersCreate', 'usersEdit', 'usersDelete'] },
  { label: 'Roles',            keys: ['rolesView', 'rolesEdit'] },
  { label: 'Hiring Requests',  keys: ['hiringRequestsSubmit', 'hiringRequestsViewOwn', 'hiringRequestsViewAll', 'hiringRequestsManage'] },
  { label: 'CV Sharing',       keys: ['cvSharesSend', 'cvSharesReceive', 'cvSharesSubmitImpression'] },
  { label: 'Recruitment',      keys: ['recruitmentBoardView', 'recruitmentManage', 'interviewFeedbackSubmit'] },
  { label: 'Candidates',       keys: ['candidateSearch'] },
  { label: 'Analytics',        keys: ['analyticsView'] },
];

export const TOTAL_PERMISSIONS = Object.keys(PERMISSION_LABELS).length;
