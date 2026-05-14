import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SettingsService } from './features/settings/services/settings';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
})
export class App {
  // Injecting here ensures the service initialises (and applies the saved theme) on startup.
  readonly settings = inject(SettingsService);
}
