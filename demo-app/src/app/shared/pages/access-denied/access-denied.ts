import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/services/auth';

@Component({
  selector: 'app-access-denied',
  imports: [RouterLink],
  templateUrl: './access-denied.html',
  styleUrl: './access-denied.scss',
})
export class AccessDenied {
  protected readonly auth = inject(AuthService);
}
