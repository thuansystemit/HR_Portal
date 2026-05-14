import {
  Component, ElementRef, HostListener, computed, inject, signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppButton } from '../../shared/components/app-button/app-button';
import { LayoutService } from '../layout.service';
import { AuthService } from '../../auth/services/auth';

@Component({
  selector: 'app-header',
  imports: [NgClass, RouterLink, AppButton],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  protected readonly layout = inject(LayoutService);
  protected readonly auth   = inject(AuthService);
  private  readonly elRef  = inject(ElementRef);

  protected menuOpen = signal(false);

  protected avatarUrl = computed(() => {
    const name = encodeURIComponent(this.auth.user()?.name ?? 'User');
    return `https://ui-avatars.com/api/?name=${name}&size=32&background=0d6efd&color=fff`;
  });

  toggleMenu(): void { this.menuOpen.update(v => !v); }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.menuOpen.set(false);
    }
  }
}
