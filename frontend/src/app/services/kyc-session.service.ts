import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface KycSession {
  id: string;
  clientName?: string;
  clientEmail?: string;
  assignedAgentId?: string;
  status?: string;
}

@Injectable({ providedIn: 'root' })
export class KycSessionService {
  constructor(private http?: HttpClient) {}

  private get api() { return environment.apiUrl; }

  getSession(id: string): Observable<KycSession> {
    if (this.api) return this.http!.get<KycSession>(`${this.api}/sessions/${id}`);
    return of({ id, clientName: 'Demo', clientEmail: 'demo@example.com' });
  }

  listSessions(): Observable<KycSession[]> {
    if (this.api) return this.http!.get<KycSession[]>(`${this.api}/sessions`);
    return of([{ id: '1', clientName: 'Demo' }]);
  }

  getSessionHistory(id: string): Observable<{ ts: string; message: string }[]> {
    if (this.api) return this.http!.get<{ ts: string; message: string }[]>(`${this.api}/sessions/${id}/history`);
    return of([
      { ts: new Date(Date.now() - 1000 * 60 * 60).toISOString(), message: 'Session créée' },
      { ts: new Date(Date.now() - 1000 * 60 * 30).toISOString(), message: 'Documents uploadés' }
    ]);
  }

  createSession(payload: any): Observable<KycSession> {
    if (this.api) return this.http!.post<KycSession>(`${this.api}/sessions`, payload);
    const created: KycSession = { id: Math.random().toString(36).substring(2, 9), clientName: payload.clientName || payload.firstName || 'New', clientEmail: payload.clientEmail || payload.email };
    return of(created);
  }

  updateSession(id: string, payload: any): Observable<KycSession> {
    if (this.api) return this.http!.patch<KycSession>(`${this.api}/sessions/${id}`, payload);
    const updated: KycSession = { id, clientName: payload.clientName || payload.firstName || 'Updated', clientEmail: payload.clientEmail || payload.email, ...(payload.status ? { status: payload.status } : {}) } as any;
    return of(updated);
  }
}
