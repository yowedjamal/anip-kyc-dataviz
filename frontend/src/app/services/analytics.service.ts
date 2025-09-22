import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  getDashboardMetrics(): Observable<any> {
    return of({ sessions: 123, active: 45 });
  }
}
