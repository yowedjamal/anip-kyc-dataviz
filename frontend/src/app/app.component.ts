import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="app-shell">
      <h1>ANIP KYC - New Frontend</h1>
      <nav>
        <a routerLink="/">Dashboard</a> |
        <a routerLink="/sessions">Sessions</a>
      </nav>
      <main>
        <router-outlet></router-outlet>
      </main>
    </div>
  `
})
export class AppComponent {}
