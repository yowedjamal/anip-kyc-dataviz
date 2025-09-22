import { Component, Inject } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';

export interface ConfirmDialogData { title?: string; message: string; confirmText?: string; cancelText?: string }

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2>{{data.title || 'Confirmer'}}</h2>
    <p>{{data.message}}</p>
    <div class="actions">
      <button mat-button (click)="onCancel()">{{data.cancelText || 'Annuler'}}</button>
      <button mat-button color="primary" (click)="onConfirm()">{{data.confirmText || 'Confirmer'}}</button>
    </div>
  `,
  styles: [`
    .actions { display:flex; gap:8px; justify-content:flex-end; padding-top:8px }
  `]
})
export class ConfirmDialogComponent {
  constructor(public dialogRef: MatDialogRef<ConfirmDialogComponent>, @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData) {}
  onConfirm() { this.dialogRef.close(true); }
  onCancel() { this.dialogRef.close(false); }
}
