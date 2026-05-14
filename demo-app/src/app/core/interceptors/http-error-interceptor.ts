import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // Centralised error handling — extend with toast/notification service
      console.error(`[HTTP ${err.status}]`, err.message);
      return throwError(() => err);
    })
  );
