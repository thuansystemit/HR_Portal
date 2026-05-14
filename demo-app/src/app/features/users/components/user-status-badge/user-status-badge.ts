import { Component, computed, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { UserStatus } from '../../models/user.model';

@Component({
  selector: 'app-user-status-badge',
  imports: [NgClass],
  templateUrl: './user-status-badge.html',
  styleUrl: './user-status-badge.scss',
})
export class UserStatusBadge {
  status = input.required<UserStatus>();

  badgeClass = computed(() => ({
    'bg-success':              this.status() === 'active',
    'bg-secondary':            this.status() === 'inactive',
    'bg-warning text-dark':    this.status() === 'pending',
  }));
}
