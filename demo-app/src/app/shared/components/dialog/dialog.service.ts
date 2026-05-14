import {
  ApplicationRef, ComponentRef, Type, createComponent,
  EnvironmentInjector, inject, Injectable,
} from '@angular/core';
import { Dialog } from './dialog';
import { DialogConfig } from './dialog.model';

@Injectable({ providedIn: 'root' })
export class DialogService {
  private appRef  = inject(ApplicationRef);
  private injector = inject(EnvironmentInjector);

  open(config: DialogConfig): Promise<boolean> {
    return new Promise<boolean>(resolve => {
      const ref: ComponentRef<Dialog> = createComponent(Dialog, {
        environmentInjector: this.injector,
      });

      ref.setInput('config', config);

      const sub = ref.instance.result.subscribe(value => {
        resolve(value);
        sub.unsubscribe();
        this.destroy(ref);
      });

      this.appRef.attachView(ref.hostView);
      document.body.appendChild(ref.location.nativeElement);
      ref.changeDetectorRef.detectChanges();
    });
  }

  /** Opens a dialog whose body is rendered by a custom component. */
  openForm<T>(
    component: Type<T>,
    config: Omit<DialogConfig, 'message' | 'bodyComponent' | 'bodyInputs'>,
    inputs?: Record<string, unknown>,
  ): Promise<boolean> {
    return this.open({ ...config, bodyComponent: component as Type<unknown>, bodyInputs: inputs });
  }

  confirm(
    title: string,
    message?: string,
    partial?: Partial<DialogConfig>,
  ): Promise<boolean> {
    return this.open({ title, message, type: 'danger', ...partial });
  }

  alert(
    title: string,
    message?: string,
    partial?: Partial<DialogConfig>,
  ): Promise<boolean> {
    return this.open({ title, message, type: 'info', showCancel: false, confirmLabel: 'OK', ...partial });
  }

  private destroy(ref: ComponentRef<Dialog>): void {
    this.appRef.detachView(ref.hostView);
    ref.destroy();
  }
}
