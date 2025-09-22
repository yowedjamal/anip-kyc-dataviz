import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Dashboard</h2>
    <p>Placeholder pour les métriques et graphiques.</p>
  `
})
export class DashboardComponent {}
