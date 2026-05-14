import {
  HttpInterceptorFn,
  HttpErrorResponse,
  HttpRequest,
  HttpHandlerFn,
  HttpEvent,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import {
  Observable, Subject, throwError, catchError, switchMap, tap, filter, take,
} from 'rxjs';
import { environment } from '../../../environments/environment';

/** Auth endpoint paths that must never trigger a refresh retry. */
const AUTH_PATHS = ['/auth/login', '/auth/logout', '/auth/refresh'];

let isRefreshing  = false;
let refreshSubject = new Subject<boolean>();

function isAuthEndpoint(url: string): boolean {
  return AUTH_PATHS.some(path => url.includes(path));
}

function doRefresh(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  http: HttpClient,
  router: Router,
): Observable<HttpEvent<unknown>> {
  if (isRefreshing) {
    // Queue the request until the ongoing refresh resolves
    return refreshSubject.pipe(
      filter(ok => ok),
      take(1),
      switchMap(() => next(req)),
    );
  }

  isRefreshing = true;
  refreshSubject = new Subject<boolean>();

  return http
    .post(`${environment.apiUrl}/auth/refresh`, {}, { withCredentials: true })
    .pipe(
      tap(() => {
        isRefreshing = false;
        refreshSubject.next(true);
        refreshSubject.complete();
      }),
      switchMap(() => next(req)),
      catchError(err => {
        isRefreshing = false;
        refreshSubject.next(false);
        refreshSubject.complete();
        router.navigate(['/login']);
        return throwError(() => err);
      }),
    );
}

export const authRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const http   = inject(HttpClient);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !isAuthEndpoint(req.url)
      ) {
        return doRefresh(req, next, http, router);
      }
      return throwError(() => err);
    }),
  );
};
