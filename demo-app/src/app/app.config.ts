import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { credentialsInterceptor }  from './core/interceptors/credentials-interceptor';
import { authRefreshInterceptor }  from './core/interceptors/auth-refresh-interceptor';
import { httpErrorInterceptor }    from './core/interceptors/http-error-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([credentialsInterceptor, authRefreshInterceptor, httpErrorInterceptor]),
    ),
  ],
};
