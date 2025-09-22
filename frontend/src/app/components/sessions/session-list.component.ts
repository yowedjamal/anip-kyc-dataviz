import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { KycSessionService, KycSession } from '../../services/kyc-session.service';

import { MatTableModule } from '@angular/material/table';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-session-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatInputModule,
    MatFormFieldModule,
    MatIconModule,
    MatButtonModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="session-list">
      <div class="header">
        <h2>Sessions</h2>
        <mat-form-field appearance="outline">
          <mat-label>Recherche</mat-label>
          <input matInput (input)="onFilter($any($event.target).value)" placeholder="nom, email, agent...">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
      </div>

      <div *ngIf="isLoading" class="loading">
        <mat-progress-spinner diameter="36" mode="indeterminate"></mat-progress-spinner>
      </div>

      <table mat-table [dataSource]="visible$ | async" class="mat-elevation-z2" *ngIf="!isLoading">

        <ng-container matColumnDef="clientName">
          <th mat-header-cell *matHeaderCellDef>Client</th>
          <td mat-cell *matCellDef="let row">{{row.clientName}}</td>
        </ng-container>

        <ng-container matColumnDef="clientEmail">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let row">{{row.clientEmail}}</td>
        </ng-container>

        <ng-container matColumnDef="assignedAgentId">
          <th mat-header-cell *matHeaderCellDef>Agent</th>
          <td mat-cell *matCellDef="let row">{{row.assignedAgentId || '-'}}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            <button mat-button color="primary" (click)="openDetail(row.id)">Voir</button>
            <button mat-button (click)="edit(row.id)">Éditer</button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>
    </div>
  `,
  styles: [`
    .session-list { padding: 16px; }
    .header { display:flex; gap:16px; align-items:center; }
    .header h2 { margin:0; }
    table { width:100%; margin-top:12px }
    .loading { display:flex; justify-content:center; padding:24px }
  `]
})
export class SessionListComponent implements OnInit {
  displayedColumns = ['clientName', 'clientEmail', 'assignedAgentId', 'actions'];
  sessions: KycSession[] = [];
  isLoading = true;

  private filter$ = new BehaviorSubject<string>('');
  visible$ = this.filter$.pipe(map(f => this.applyFilter(f)));

  constructor(private sessionService: KycSessionService, private router: Router) {}

  ngOnInit(): void {
    this.sessionService.listSessions().subscribe(list => { this.sessions = list || []; this.isLoading = false; this.filter$.next(this.filter$.value); });
  }

  onFilter(value: string) { this.filter$.next(value?.trim().toLowerCase() || ''); }

  private applyFilter(filter: string) {
    if (!filter) return this.sessions.slice();
    return this.sessions.filter(s => {
      const hay = `${s.clientName || ''} ${s.clientEmail || ''} ${s.assignedAgentId || ''}`.toLowerCase();
      return hay.indexOf(filter) !== -1;
    });
  }

  openDetail(id: string) { this.router.navigate(['/sessions', id]); }
  edit(id: string) { this.router.navigate(['/sessions', id, 'edit']); }
}

