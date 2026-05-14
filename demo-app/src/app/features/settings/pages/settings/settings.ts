import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder } from '@angular/forms';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import {
  SettingsService, Theme, Language, DateFormat, PageSize,
} from '../../services/settings';

@Component({
  selector: 'app-settings',
  imports: [...SHARED_IMPORTS],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class SettingsPage implements OnInit {
  private readonly svc     = inject(SettingsService);
  private readonly fb      = inject(FormBuilder);
  private readonly destroy = inject(DestroyRef);

  protected saved  = signal(false);
  protected saving = signal(false);

  protected form = this.fb.group({
    // Appearance
    theme:    [this.svc.settings().theme],
    // Localization
    language:   [this.svc.settings().language],
    dateFormat: [this.svc.settings().dateFormat],
    // Table
    tablePageSize: [this.svc.settings().tablePageSize],
    // Notifications
    emailNotifications:   [this.svc.settings().emailNotifications],
    pushNotifications:    [this.svc.settings().pushNotifications],
    desktopNotifications: [this.svc.settings().desktopNotifications],
  });

  ngOnInit(): void {
    this.form.get('theme')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(theme => {
        if (theme) this.svc.applyTheme(theme as Theme);
      });
  }

  protected readonly themes: { value: Theme; label: string; icon: string }[] = [
    { value: 'light',  label: 'Light',  icon: 'bi-sun-fill'        },
    { value: 'dark',   label: 'Dark',   icon: 'bi-moon-stars-fill' },
    { value: 'system', label: 'System', icon: 'bi-circle-half'     },
  ];

  protected readonly languages: { value: Language; label: string }[] = [
    { value: 'en', label: 'English'    },
    { value: 'vi', label: 'Tiếng Việt' },
    { value: 'fr', label: 'Français'   },
    { value: 'de', label: 'Deutsch'    },
    { value: 'ja', label: '日本語'      },
  ];

  protected readonly dateFormats: { value: DateFormat; label: string }[] = [
    { value: 'MM/DD/YYYY', label: 'MM/DD/YYYY  (e.g. 05/09/2026)' },
    { value: 'DD/MM/YYYY', label: 'DD/MM/YYYY  (e.g. 09/05/2026)' },
    { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD  (e.g. 2026-05-09)' },
  ];

  protected readonly pageSizes: { value: PageSize; label: string }[] = [
    { value: 5,  label: '5 rows'  },
    { value: 10, label: '10 rows' },
    { value: 25, label: '25 rows' },
    { value: 50, label: '50 rows' },
  ];

  async save(): Promise<void> {
    this.saving.set(true);
    this.saved.set(false);
    await new Promise(r => setTimeout(r, 400));
    const v = this.form.getRawValue();
    this.svc.update({
      theme:                v.theme        as Theme,
      language:             v.language     as Language,
      dateFormat:           v.dateFormat   as DateFormat,
      tablePageSize:        Number(v.tablePageSize) as PageSize,
      emailNotifications:   !!v.emailNotifications,
      pushNotifications:    !!v.pushNotifications,
      desktopNotifications: !!v.desktopNotifications,
    });
    this.saving.set(false);
    this.saved.set(true);
    setTimeout(() => this.saved.set(false), 3000);
  }

  reset(): void {
    this.svc.reset();
    const s = this.svc.settings();
    this.form.reset({
      theme: s.theme, language: s.language, dateFormat: s.dateFormat,
      tablePageSize: s.tablePageSize,
      emailNotifications: s.emailNotifications,
      pushNotifications: s.pushNotifications,
      desktopNotifications: s.desktopNotifications,
    });
  }
}
