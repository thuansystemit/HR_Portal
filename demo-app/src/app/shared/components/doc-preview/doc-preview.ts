import {
  AfterViewInit, Component, ElementRef, HostBinding, OnDestroy, OnInit,
  ViewChild, inject, input, signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { NgClass } from '@angular/common';
import { renderAsync } from 'docx-preview';

type PreviewMode = 'pdf' | 'docx' | 'doc' | 'unsupported';

@Component({
  selector: 'app-doc-preview',
  imports: [NgClass],
  templateUrl: './doc-preview.html',
  styleUrl: './doc-preview.scss',
  host: { class: 'dp-host' },
})
export class DocPreview implements OnInit, AfterViewInit, OnDestroy {
  /** Pass a browser File for pre-upload preview. */
  file   = input<File | null>(null);
  /** Pass a URL to fetch (with auth cookies) for post-upload preview. */
  src    = input<string>('');
  height = input<string>('520px');

  private readonly sanitizer = inject(DomSanitizer);

  protected mode     = signal<PreviewMode>('pdf');
  protected pdfSrc   = signal<SafeResourceUrl | null>(null);
  protected loading  = signal(false);
  protected hasError = signal(false);

  private blobUrl: string | null = null;
  private docxFile: File | null  = null;
  private docxRendered            = false;

  @ViewChild('docxContainer') docxContainer?: ElementRef<HTMLDivElement>;

  @HostBinding('style.--dp-height')
  get heightVar(): string { return this.height(); }

  ngOnInit(): void {
    const src = this.src();
    if (src) {
      this.loading.set(true);
      fetch(src, { credentials: 'include' })
        .then(r => {
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          return r.blob();
        })
        .then(blob => {
          const parts   = src.split('/');
          const rawName = parts[parts.length - 1].split('?')[0];
          const name    = decodeURIComponent(rawName);
          this.initFromFile(new File([blob], name, { type: blob.type }), true);
        })
        .catch(() => { this.loading.set(false); this.hasError.set(true); });
    } else {
      const f = this.file();
      if (f) this.initFromFile(f, false);
    }
  }

  private initFromFile(f: File, fromUrl: boolean): void {
    const name = f.name.toLowerCase();

    if (f.type === 'application/pdf' || name.endsWith('.pdf')) {
      this.mode.set('pdf');
      this.blobUrl = URL.createObjectURL(f);
      this.pdfSrc.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.blobUrl));
      this.loading.set(false);

    } else if (
      name.endsWith('.docx') ||
      f.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    ) {
      this.docxFile = f;
      this.mode.set('docx');
      if (fromUrl) {
        // ngAfterViewInit already ran; wait one tick for Angular to render #docxContainer
        setTimeout(() => this.renderDocx(), 0);
      }
      // sync case (file input): ngAfterViewInit will call renderDocx()

    } else if (name.endsWith('.doc') || f.type === 'application/msword') {
      this.mode.set('doc');
      this.loading.set(false);

    } else {
      this.mode.set('unsupported');
      this.loading.set(false);
    }
  }

  ngAfterViewInit(): void {
    if (this.mode() === 'docx') this.renderDocx();
  }

  private renderDocx(): void {
    if (!this.docxContainer || !this.docxFile || this.docxRendered) return;
    this.docxRendered = true;
    this.loading.set(true);
    renderAsync(this.docxFile, this.docxContainer.nativeElement, undefined, {
      inWrapper: false,
      className: 'dp-docx-body',
      breakPages: true,
    })
      .then(() => this.loading.set(false))
      .catch(() => { this.loading.set(false); this.hasError.set(true); });
  }

  ngOnDestroy(): void {
    if (this.blobUrl) URL.revokeObjectURL(this.blobUrl);
  }

  protected download(): void {
    const src = this.src();
    if (src) { window.open(src, '_blank'); return; }
    const f = this.file();
    if (!f) return;
    const url = URL.createObjectURL(f);
    const a   = document.createElement('a');
    a.href     = url;
    a.download = f.name;
    a.click();
    URL.revokeObjectURL(url);
  }
}
