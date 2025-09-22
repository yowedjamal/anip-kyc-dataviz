import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { Observable, of, startWith, map } from 'rxjs';
import { KycSessionService, KycSession } from '../../services/kyc-session.service';

interface Agent { id: string; name: string; email?: string }

@Component({
  selector: 'app-session-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatAutocompleteModule],
  template: `
    <h2>Édition session</h2>
    <form [formGroup]="sessionForm" (ngSubmit)="onSubmit()">
      <div>
        <label>Prénom</label>
        <input formControlName="firstName">
      </div>
      <div>
        <label>Nom</label>
        <input formControlName="lastName">
      </div>
      <div>
        <label>Email</label>
        <input formControlName="email">
      </div>

      <div>
        <label>Agent assigné</label>
        <input [matAutocomplete]="auto" formControlName="assignedAgentId">
        <mat-autocomplete #auto="matAutocomplete">
          <mat-option *ngFor="let a of filteredAgents | async" [value]="a.id">{{a.name}} - {{a.email}}</mat-option>
        </mat-autocomplete>
      </div>

      <div>
        <button type="submit" [disabled]="sessionForm.invalid">Enregistrer</button>
      </div>
    </form>
  `
})
export class SessionEditComponent implements OnInit {
  sessionForm!: FormGroup;
  sessionId: string | null = null;
  agents: Agent[] = [];
  filteredAgents!: Observable<Agent[]>;

  constructor(private fb: FormBuilder, private sessionService: KycSessionService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.sessionForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      assignedAgentId: ['']
    });

    // load agents (mock)
    this.agents = [
      { id: '1', name: 'Jean Dupont', email: 'jean.dupont@example.com' },
      { id: '2', name: 'Marie Martin', email: 'marie.martin@example.com' }
    ];

    this.filteredAgents = this.sessionForm.get('assignedAgentId')!.valueChanges.pipe(
      startWith(''),
      map(value => this._filterAgents(value))
    );

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.sessionId = id;
      this.sessionService.getSession(id).subscribe(s => this.populateForm(s));
    }
  }

  private _filterAgents(value: string) {
    const v = (value || '').toString().toLowerCase();
    return this.agents.filter(a => a.name.toLowerCase().includes(v) || (a.email || '').toLowerCase().includes(v));
  }

  private populateForm(s: KycSession) {
    this.sessionForm.patchValue({
      firstName: s.clientName || '',
      lastName: '',
      email: s.clientEmail || '',
      assignedAgentId: s.assignedAgentId || ''
    });
  }

  onSubmit() {
    if (this.sessionForm.invalid) return;
    const v = this.sessionForm.value;
    if (this.sessionId) {
      // update
      this.sessionService.updateSession(this.sessionId, { ...v }).subscribe(() => this.router.navigate(['/sessions', this.sessionId]));
    } else {
      this.sessionService.createSession({ ...v }).subscribe(s => this.router.navigate(['/sessions', s.id]));
    }
  }
}
