import { Injectable } from '@angular/core';
import { DialogFormBody } from './dialog.model';

/** Scoped to each Dialog instance via `providers: [DialogFormRegistry]`. */
@Injectable()
export class DialogFormRegistry {
  private _body?: DialogFormBody;

  register(body: DialogFormBody): void   { this._body = body; }
  unregister(): void                      { this._body = undefined; }
  get instance(): DialogFormBody | undefined { return this._body; }
}
