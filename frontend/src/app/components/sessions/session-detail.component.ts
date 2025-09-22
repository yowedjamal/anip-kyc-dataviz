import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { KycSessionService, KycSession } from '../../services/kyc-session.service';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../shared/confirm-dialog.component';

@Component({
  selector: 'app-session-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="detail">
      <div *ngIf="isLoading" class="loading"><mat-progress-spinner diameter="36" mode="indeterminate"></mat-progress-spinner></div>

      <mat-card *ngIf="session">
        <mat-card-title>Session: {{session.clientName || session.id}}</mat-card-title>
        <mat-card-content>
          <p><strong>Email:</strong> {{session.clientEmail || '-'}}</p>
          <p><strong>Agent:</strong> {{session.assignedAgentId || '-'}}</p>
          <p><strong>Statut:</strong> {{session?.status || 'new'}}</p>
        </mat-card-content>
        <mat-card-actions>
          <button mat-button color="primary" (click)="edit()">Éditer</button>
          <button mat-button color="accent" (click)="validate()" [disabled]="isProcessing">Valider</button>
          <button mat-button color="warn" (click)="reject()" [disabled]="isProcessing">Rejeter</button>
          <button mat-button (click)="back()">Retour</button>
        </mat-card-actions>
      </mat-card>

      <div *ngIf="!isLoading && !session">Aucune session trouvée.</div>

      <mat-card *ngIf="timeline && timeline.length">
        <mat-card-title>Timeline</mat-card-title>
        <mat-card-content>
          <ul>
            <li *ngFor="let t of timeline">{{t.ts | date:'short'}} - {{t.message}}</li>
          </ul>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .detail { padding:16px }
    .loading { display:flex; justify-content:center; padding:24px }
  `]
})
export class SessionDetailComponent implements OnInit {
  session: KycSession | null = null;
  isLoading = true;
  isProcessing = false;
  timeline: { ts: string; message: string }[] = [];

  constructor(private route: ActivatedRoute, private router: Router, private sessionService: KycSessionService, private dialog: MatDialog) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.isLoading = false; return; }
    this.sessionService.getSession(id).subscribe(s => { this.session = s || null; this.isLoading = false; });
    this.sessionService.getSessionHistory(id).subscribe(h => { this.timeline = h || []; });
  }

  edit() { if (!this.session) return; this.router.navigate(['/sessions', this.session.id, 'edit']); }
  back() { this.router.navigate(['/sessions']); }

  validate() {
    if (!this.session) return;
    const ref = this.dialog.open(ConfirmDialogComponent, { data: { title: 'Valider la session', message: 'Confirmer la validation de cette session ?', confirmText: 'Valider', cancelText: 'Annuler' } });
    ref.afterClosed().subscribe(ok => {
      if (!ok) return;
      this.isProcessing = true;
      this.sessionService.updateSession(this.session!.id, { status: 'validated' }).subscribe(updated => {
        this.isProcessing = false;
        this.session = updated;
        this.timeline.unshift({ ts: new Date().toISOString(), message: 'Session validée' });
      });
    });
  }

  reject() {
    if (!this.session) return;
    const ref = this.dialog.open(ConfirmDialogComponent, { data: { title: 'Rejeter la session', message: 'Confirmer le rejet de cette session ?', confirmText: 'Rejeter', cancelText: 'Annuler' } });
    ref.afterClosed().subscribe(ok => {
      if (!ok) return;
      this.isProcessing = true;
      this.sessionService.updateSession(this.session!.id, { status: 'rejected' }).subscribe(updated => {
        this.isProcessing = false;
        this.session = updated;
        this.timeline.unshift({ ts: new Date().toISOString(), message: 'Session rejetée' });
      });
    });
  }
}

