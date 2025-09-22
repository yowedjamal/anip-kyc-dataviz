import { Route } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { SessionListComponent } from './components/sessions/session-list.component';
import { SessionDetailComponent } from './components/sessions/session-detail.component';
import { SessionEditComponent } from './components/sessions/session-edit.component';

export const routes: Route[] = [
  { path: '', component: DashboardComponent },
  { path: 'sessions', component: SessionListComponent },
  { path: 'sessions/:id', component: SessionDetailComponent },
  { path: 'sessions/:id/edit', component: SessionEditComponent },
  { path: '**', redirectTo: '' }
];
