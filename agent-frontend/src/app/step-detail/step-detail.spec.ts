import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StepDetail } from './step-detail';

describe('StepDetail', () => {
  let component: StepDetail;
  let fixture: ComponentFixture<StepDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StepDetail],
    }).compileComponents();

    fixture = TestBed.createComponent(StepDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
