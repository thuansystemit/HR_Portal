import {
  Component, ElementRef, HostListener, OnDestroy, OnInit,
  computed, inject, signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppButton } from '../../shared/components/app-button/app-button';
import { AppDatePipe } from '../../shared/pipes/app-date.pipe';
import { LayoutService } from '../layout.service';
import { AuthService } from '../../auth/services/auth';
import { NotificationApi, Notification } from '../../core/services/notification.api';

@Component({
  selector: 'app-header',
  imports: [NgClass, RouterLink, AppButton, AppDatePipe],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header implements OnInit, OnDestroy {
  protected readonly layout          = inject(LayoutService);
  protected readonly auth            = inject(AuthService);
  private  readonly elRef            = inject(ElementRef);
  private  readonly notificationApi  = inject(NotificationApi);

  protected menuOpen    = signal(false);
  protected notifOpen   = signal(false);
  protected notifications = signal<Notification[]>([]);
  protected unreadCount   = signal(0);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  protected avatarUrl = computed(() => {
    const name = encodeURIComponent(this.auth.user()?.name ?? 'User');
    return `https://ui-avatars.com/api/?name=${name}&size=32&background=0d6efd&color=fff`;
  });

  ngOnInit(): void {
    this.loadUnreadCount();
    this.pollHandle = setInterval(() => this.loadUnreadCount(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
    }
  }

  toggleMenu(): void { this.menuOpen.update(v => !v); }

  toggleNotifications(): void {
    this.notifOpen.update(v => !v);
    if (this.notifOpen()) {
      this.notificationApi.list().subscribe({
        next: list => this.notifications.set(list),
        error: ()   => {},
      });
    }
  }

  loadUnreadCount(): void {
    this.notificationApi.getUnreadCount().subscribe({
      next: res => this.unreadCount.set(res.count),
      error: ()  => {},
    });
  }

  markRead(id: string): void {
    const notif = this.notifications().find(n => n.id === id);
    if (!notif || notif.isRead) return;

    this.notificationApi.markRead(id).subscribe({
      next: () => {
        this.notifications.update(list =>
          list.map(n => n.id === id ? { ...n, isRead: true } : n),
        );
        this.unreadCount.update(c => Math.max(0, c - 1));
      },
      error: () => {},
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.menuOpen.set(false);
      this.notifOpen.set(false);
    }
  }
}
