import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'light' | 'dark' | 'system';
export type Language = 'en' | 'vi' | 'fr' | 'de' | 'ja';
export type DateFormat = 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';
export type PageSize = 5 | 10 | 25 | 50;

export interface AppSettings {
  theme:                Theme;
  language:             Language;
  dateFormat:           DateFormat;
  tablePageSize:        PageSize;
  emailNotifications:   boolean;
  pushNotifications:    boolean;
  desktopNotifications: boolean;
}

const STORAGE_KEY = 'app_settings';

const DEFAULTS: AppSettings = {
  theme:                'light',
  language:             'en',
  dateFormat:           'MM/DD/YYYY',
  tablePageSize:        10,
  emailNotifications:   true,
  pushNotifications:    false,
  desktopNotifications: false,
};

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly _settings = signal<AppSettings>(this.load());
  readonly settings = this._settings.asReadonly();

  constructor() {
    this.applyTheme(this._settings().theme);
  }

  update(patch: Partial<AppSettings>): void {
    const next = { ...this._settings(), ...patch };
    this._settings.set(next);
    this.persist(next);
    this.applyTheme(next.theme);
  }

  reset(): void {
    const defaults = { ...DEFAULTS };
    this._settings.set(defaults);
    this.persist(defaults);
    this.applyTheme(defaults.theme);
  }

  applyTheme(theme: Theme): void {
    if (!this.isBrowser) return;
    const resolved = theme === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : theme;
    document.documentElement.setAttribute('data-theme', resolved);
  }

  private load(): AppSettings {
    if (!this.isBrowser) return { ...DEFAULTS };
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : { ...DEFAULTS };
    } catch {
      return { ...DEFAULTS };
    }
  }

  private persist(s: AppSettings): void {
    if (!this.isBrowser) return;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
  }
}
