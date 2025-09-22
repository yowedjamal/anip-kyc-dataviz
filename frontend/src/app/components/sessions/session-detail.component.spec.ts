import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SessionDetailComponent } from './session-detail.component';
import { KycSessionService } from '../../services/kyc-session.service';
import { of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';

describe('SessionDetailComponent', () => {
  let component: SessionDetailComponent;
  let fixture: ComponentFixture<SessionDetailComponent>;
  let sessionService: jasmine.SpyObj<KycSessionService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  beforeEach(async () => {
    const svc = jasmine.createSpyObj('KycSessionService', ['getSession', 'getSessionHistory', 'updateSession']);
    svc.getSession.and.returnValue(of({ id: '1', clientName: 'A' } as any));
    svc.getSessionHistory.and.returnValue(of([]));
    svc.updateSession.and.returnValue(of({ id: '1', clientName: 'A', status: 'validated' } as any));

    const dlg = jasmine.createSpyObj('MatDialog', ['open']);
    dlg.open.and.returnValue({ afterClosed: () => of(true) } as any);

    await TestBed.configureTestingModule({
      imports: [SessionDetailComponent],
      providers: [
        { provide: KycSessionService, useValue: svc },
        { provide: MatDialog, useValue: dlg }
      ]
    }).compileComponents();

    sessionService = TestBed.inject(KycSessionService) as jasmine.SpyObj<KycSessionService>;
    dialog = TestBed.inject(MatDialog) as jasmine.SpyObj<MatDialog>;
    fixture = TestBed.createComponent(SessionDetailComponent);
    component = fixture.componentInstance;
    // set route snapshot param manually
    (component as any).route = { snapshot: { paramMap: { get: () => '1' } } };
    fixture.detectChanges();
  });

  it('should validate on confirm', fakeAsync(() => {
    component.validate();
    tick();
    expect(sessionService.updateSession).toHaveBeenCalledWith('1', { status: 'validated' });
    expect(component.session?.status).toBe('validated');
  }));

  it('should reject on confirm', fakeAsync(() => {
    component.reject();
    tick();
    expect(sessionService.updateSession).toHaveBeenCalledWith('1', { status: 'rejected' });
    expect(component.session?.status).toBe('rejected');
  }));
});
