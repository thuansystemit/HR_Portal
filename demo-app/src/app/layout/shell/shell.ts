import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';
import { Footer } from '../footer/footer';
import { LayoutService } from '../layout.service';
import { InactivityService } from '../../core/services/inactivity.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Header, Sidebar, Footer],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit, OnDestroy {
  protected readonly layout     = inject(LayoutService);
  private  readonly inactivity = inject(InactivityService);

  ngOnInit():  void { this.inactivity.start(); }
  ngOnDestroy(): void { this.inactivity.stop(); }
}
