import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * Attaches `withCredentials: true` to every request that targets our own API
 * so that the browser sends the HttpOnly auth cookies cross-origin.
 * Requests to external services (e.g. MinIO presigned URLs) are left untouched.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl)) {
    return next(req);
  }
  return next(req.clone({ withCredentials: true }));
};
